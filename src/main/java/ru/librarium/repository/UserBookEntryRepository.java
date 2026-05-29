package ru.librarium.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.librarium.entity.UserBookEntry;

import java.util.List;
import java.util.Optional;

public interface UserBookEntryRepository extends JpaRepository<UserBookEntry, Long> {
    @EntityGraph(attributePaths = {"book"})
    List<UserBookEntry> findByUserUidOrderByUpdatedAtDesc(String userUid);

    Optional<UserBookEntry> findByUserUidAndBookId(String userUid, Long bookId);

    void deleteByBookId(Long bookId);
}
