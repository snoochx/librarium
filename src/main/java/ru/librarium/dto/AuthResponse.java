package ru.librarium.dto;

public record AuthResponse(String uid, String email, String displayName, String photoUrl, String role) {}
