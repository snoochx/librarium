package ru.librarium.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.librarium.config.AppProperties;
import ru.librarium.entity.Role;
import ru.librarium.entity.SessionUser;
import ru.librarium.entity.UserProfile;
import ru.librarium.repository.UserProfileRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    public static final String SESSION_KEY = "LIBRARIUM_CURRENT_USER";
    private static final String REMEMBER_COOKIE = "LIBRARIUM_REMEMBER";

    private final FirebaseTokenVerifier tokenVerifier;
    private final UserProfileRepository userProfileRepository;
    private final AppProperties properties;

    @Transactional
    public SessionUser login(String idToken, HttpSession session) {
        return login(idToken, session, null);
    }

    @Transactional
    public SessionUser login(String idToken, HttpSession session, HttpServletResponse response) {
        FirebaseUserClaims claims = tokenVerifier.verify(idToken);

        UserProfile profile = userProfileRepository.findById(claims.uid()).orElseGet(UserProfile::new);
        boolean isNewProfile = profile.getUid() == null;

        profile.setUid(claims.uid());
        profile.setEmail(claims.email());

        String existingDisplayName = profile.getDisplayName();
        if (isNewProfile || existingDisplayName == null || existingDisplayName.isBlank()) {
            profile.setDisplayName(normalizeDisplayName(claims.displayName(), claims.email()));
        } else {
            profile.setDisplayName(existingDisplayName.trim());
        }

        if (profile.getPhotoUrl() == null || profile.getPhotoUrl().isBlank()) {
            profile.setPhotoUrl(claims.photoUrl());
        }

        profile.setRole(resolveRole(claims.email()));
        userProfileRepository.save(profile);

        SessionUser current = toSessionUser(profile);
        session.setAttribute(SESSION_KEY, current);

        writeRememberCookie(response, current.uid());
        return current;
    }

    public Optional<SessionUser> restoreRememberedSession(HttpServletRequest request, HttpServletResponse response) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            Object obj = existingSession.getAttribute(SESSION_KEY);
            if (obj instanceof SessionUser sessionUser) {
                return Optional.of(sessionUser);
            }
        }

        Optional<String> rememberToken = readCookie(request, REMEMBER_COOKIE);
        if (rememberToken.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> rememberedUid = verifyRememberToken(rememberToken.get());
        if (rememberedUid.isEmpty()) {
            clearRememberCookie(response);
            return Optional.empty();
        }

        Optional<UserProfile> profile = userProfileRepository.findById(rememberedUid.get());
        if (profile.isEmpty()) {
            clearRememberCookie(response);
            return Optional.empty();
        }

        SessionUser current = toSessionUser(profile.get());
        request.getSession(true).setAttribute(SESSION_KEY, current);
        return Optional.of(current);
    }

    public Optional<SessionUser> currentUser(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object obj = session.getAttribute(SESSION_KEY);
        if (obj instanceof SessionUser sessionUser) {
            return Optional.of(sessionUser);
        }
        return Optional.empty();
    }

    public void logout(HttpSession session) {
        logout(session, null);
    }

    public void logout(HttpSession session, HttpServletResponse response) {
        if (session != null) {
            session.invalidate();
        }
        clearRememberCookie(response);
    }

    @Transactional(readOnly = true)
    public SessionUser requireUser(HttpSession session) {
        return currentUser(session).orElseThrow(() -> new IllegalStateException("User is not authenticated"));
    }

    @Transactional(readOnly = true)
    public UserProfile requireProfile(HttpSession session) {
        SessionUser user = requireUser(session);
        return userProfileRepository.findById(user.uid())
                .orElseThrow(() -> new IllegalStateException("Profile not found"));
    }

    public boolean isAdmin(HttpSession session) {
        return currentUser(session).map(SessionUser::isAdmin).orElse(false);
    }

    @Transactional
    public SessionUser updateDisplayName(HttpSession session, String displayName) {
        SessionUser current = requireUser(session);
        UserProfile profile = userProfileRepository.findById(current.uid())
                .orElseThrow(() -> new IllegalStateException("Profile not found"));

        profile.setDisplayName(normalizeDisplayName(displayName, profile.getEmail()));
        userProfileRepository.save(profile);

        SessionUser updated = toSessionUser(profile);
        session.setAttribute(SESSION_KEY, updated);
        return updated;
    }

    private void writeRememberCookie(HttpServletResponse response, String uid) {
        if (response == null) {
            return;
        }

        Cookie cookie = new Cookie(REMEMBER_COOKIE, createRememberToken(uid));
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(Math.toIntExact(Duration.ofDays(Math.max(1, properties.getAuth().getRememberDays())).getSeconds()));
        cookie.setSecure(false); // если HTTPS — лучше поставить true
        response.addCookie(cookie);
    }

    private void clearRememberCookie(HttpServletResponse response) {
        if (response == null) {
            return;
        }

        Cookie cookie = new Cookie(REMEMBER_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(false);
        response.addCookie(cookie);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private String createRememberToken(String uid) {
        long expiresAt = Instant.now()
                .plus(Duration.ofDays(Math.max(1, properties.getAuth().getRememberDays())))
                .toEpochMilli();

        String payload = base64(uid) + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    private Optional<String> verifyRememberToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String payload = parts[0] + "." + parts[1];
            String expectedSignature = sign(payload);

            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            long expiresAt = Long.parseLong(parts[1]);
            if (Instant.now().toEpochMilli() > expiresAt) {
                return Optional.empty();
            }

            return Optional.of(fromBase64(parts[0]));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                    properties.getAuth().getRememberSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(key);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign remember token", ex);
        }
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String fromBase64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private Role resolveRole(String email) {
        return properties.getAdminEmails().stream()
                .anyMatch(admin -> admin.equalsIgnoreCase(email))
                ? Role.ADMIN
                : Role.USER;
    }

    private SessionUser toSessionUser(UserProfile profile) {
        return new SessionUser(
                profile.getUid(),
                profile.getEmail(),
                profile.getDisplayName(),
                profile.getPhotoUrl(),
                profile.getRole()
        );
    }

    private String normalizeDisplayName(String displayName, String fallbackEmail) {
        if (displayName == null || displayName.isBlank()) {
            int at = fallbackEmail == null ? -1 : fallbackEmail.indexOf('@');
            return at > 0 ? fallbackEmail.substring(0, at) : "Пользователь";
        }
        return displayName.trim();
    }
}