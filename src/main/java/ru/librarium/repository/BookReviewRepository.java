package ru.librarium.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.librarium.entity.BookReview;

import java.util.List;
import java.util.Optional;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    @EntityGraph(attributePaths = {"book"})
    List<BookReview> findByBookIdOrderByCreatedAtDesc(Long bookId);

    @EntityGraph(attributePaths = {"book"})
    List<BookReview> findByReviewerUidOrderByUpdatedAtDesc(String reviewerUid);

    Optional<BookReview> findByBookIdAndReviewerUid(Long bookId, String reviewerUid);

    void deleteByBookId(Long bookId);

    void deleteByBookIdAndReviewerUid(Long bookId, String reviewerUid);
}
