package ru.librarium.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.librarium.dto.BookSuggestionForm;
import ru.librarium.entity.BookSuggestion;
import ru.librarium.entity.SessionUser;
import ru.librarium.entity.SuggestionStatus;
import ru.librarium.repository.BookSuggestionRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SuggestionService {
    private final BookSuggestionRepository suggestionRepository;
    private final BookService bookService;

    public BookSuggestion submit(SessionUser user, BookSuggestionForm form) {
        BookSuggestion suggestion = new BookSuggestion();
        suggestion.setTitle(form.getTitle().trim());
        suggestion.setAuthor(form.getAuthor().trim());
        suggestion.setPublicationYear(form.getPublicationYear());
        suggestion.setDescription(form.getDescription());
        suggestion.setSubmittedByUid(user.uid());
        suggestion.setSubmittedByEmail(user.email());
        suggestion.setSubmittedByName(user.displayName());
        suggestion.setStatus(SuggestionStatus.PENDING);
        return suggestionRepository.save(suggestion);
    }

    @Transactional(readOnly = true)
    public List<BookSuggestion> pendingSuggestions() {
        return suggestionRepository.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<BookSuggestion> allSuggestions() {
        return suggestionRepository.findAllByOrderByCreatedAtDesc();
    }

    public BookSuggestion approve(Long id, SessionUser admin) {
        BookSuggestion suggestion = suggestionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        suggestion.setStatus(SuggestionStatus.APPROVED);
        suggestion.setReviewedByEmail(admin.email());
        suggestion.setReviewedAt(Instant.now());
        suggestion.setModeratorComment("Одобрено администратором");
        return suggestionRepository.save(suggestion);
    }

    public void reject(Long id, SessionUser admin, String comment) {
        BookSuggestion suggestion = suggestionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setReviewedByEmail(admin.email());
        suggestion.setReviewedAt(Instant.now());
        suggestion.setModeratorComment(comment == null || comment.isBlank() ? "Отклонено администратором" : comment);
        suggestionRepository.save(suggestion);
    }
}
