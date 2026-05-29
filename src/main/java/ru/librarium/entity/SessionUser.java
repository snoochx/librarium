package ru.librarium.entity;

import java.io.Serializable;

public record SessionUser(String uid, String email, String displayName, String photoUrl, Role role) implements Serializable {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
