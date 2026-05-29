package ru.librarium.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.librarium.config.AppProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirebaseTokenVerifier {
    private static final URI CERT_URI = URI.create("https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com");
    private final AppProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private volatile Map<String, RSAPublicKey> cachedKeys = new HashMap<>();
    private volatile Instant cacheValidUntil = Instant.EPOCH;

    public FirebaseUserClaims verify(String idToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("Unsupported token algorithm");
            }

            String kid = jwt.getHeader().getKeyID();
            RSAPublicKey key = resolveKey(kid);
            JWSVerifier verifier = new RSASSAVerifier(key);
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("Invalid Firebase signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            validateClaims(claims);
            return new FirebaseUserClaims(
                    claims.getSubject(),
                    claims.getStringClaim("email"),
                    firstNonBlank(claims.getStringClaim("name"), claims.getStringClaim("email")),
                    claims.getStringClaim("picture"),
                    Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"))
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Firebase token verification failed: " + ex.getMessage(), ex);
        }
    }

    private void validateClaims(JWTClaimsSet claims) throws Exception {
        String projectId = properties.getFirebase().getProjectId();
        String issuer = "https://securetoken.google.com/" + projectId;
        if (!issuer.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid issuer");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(projectId)) {
            throw new IllegalArgumentException("Invalid audience");
        }
        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
            throw new IllegalArgumentException("Token expired");
        }
    }

    private synchronized RSAPublicKey resolveKey(String kid) throws Exception {
        if (kid != null && Instant.now().isBefore(cacheValidUntil) && cachedKeys.containsKey(kid)) {
            return cachedKeys.get(kid);
        }
        refreshKeys();
        RSAPublicKey key = cachedKeys.get(kid);
        if (key == null) {
            refreshKeys();
            key = cachedKeys.get(kid);
        }
        if (key == null) {
            throw new IllegalArgumentException("No Firebase public key for kid=" + kid);
        }
        return key;
    }

    private void refreshKeys() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(CERT_URI).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to load Firebase certificates: HTTP " + response.statusCode());
        }
        Map<String, String> certMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Map<String, RSAPublicKey> fresh = new HashMap<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (Map.Entry<String, String> entry : certMap.entrySet()) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(entry.getValue().getBytes())
            );
            fresh.put(entry.getKey(), (RSAPublicKey) cert.getPublicKey());
        }
        cachedKeys = fresh;
        cacheValidUntil = Instant.now().plus(Duration.ofHours(1));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return "Librarium user";
    }
}
