package ru.librarium.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.librarium.entity.SessionUser;
import ru.librarium.service.AuthService;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {
    private final AuthService authService;

    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        SessionUser current = authService.currentUser(session).orElse(null);
        if (current != null) {
            return current;
        }
        return authService.restoreRememberedSession(request, response).orElse(null);
    }

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        if (authService.currentUser(session).isPresent()) {
            return true;
        }
        return authService.restoreRememberedSession(request, response).isPresent();
    }
}
