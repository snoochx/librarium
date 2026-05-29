package ru.librarium.service;

public record FirebaseUserClaims(
        String uid,
        String email,
        String displayName,
        String photoUrl,
        boolean emailVerified
) {}
