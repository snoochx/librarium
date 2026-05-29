package ru.librarium.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.librarium.dto.AuthResponse;
import ru.librarium.dto.LoginRequest;
import ru.librarium.entity.SessionUser;
import ru.librarium.service.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request, HttpSession session) {
        SessionUser current = authService.login(request.getIdToken(), session);
        return ResponseEntity.ok(new AuthResponse(current.uid(), current.email(), current.displayName(), current.photoUrl(), current.role().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(HttpSession session) {
        return authService.currentUser(session)
                .map(user -> ResponseEntity.ok(new AuthResponse(user.uid(), user.email(), user.displayName(), user.photoUrl(), user.role().name())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
