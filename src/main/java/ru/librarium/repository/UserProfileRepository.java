package ru.librarium.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.librarium.entity.UserProfile;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmailIgnoreCase(String email);
}
