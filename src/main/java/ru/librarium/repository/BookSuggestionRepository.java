package ru.librarium.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.librarium.entity.BookSuggestion;
import ru.librarium.entity.SuggestionStatus;

import java.util.List;

public interface BookSuggestionRepository extends JpaRepository<BookSuggestion, Long> {
    List<BookSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);
    List<BookSuggestion> findAllByOrderByCreatedAtDesc();
}
