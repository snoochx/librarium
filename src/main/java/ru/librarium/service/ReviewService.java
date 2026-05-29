package ru.librarium.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.librarium.dto.ReviewForm;
import ru.librarium.entity.Book;
import ru.librarium.entity.BookReview;
import ru.librarium.entity.SessionUser;
import ru.librarium.repository.BookRepository;
import ru.librarium.repository.BookReviewRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {
    private final BookRepository bookRepository;
    private final BookReviewRepository reviewRepository;
    private final BookService bookService;

    @Transactional(readOnly = true)
    public List<BookReview> reviewsForBook(Long bookId) {
        return reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId);
    }

    @Transactional(readOnly = true)
    public List<BookReview> reviewsForUser(String uid) {
        return reviewRepository.findByReviewerUidOrderByUpdatedAtDesc(uid);
    }

    @Transactional(readOnly = true)
    public Optional<BookReview> reviewForUser(Long bookId, String uid) {
        return reviewRepository.findByBookIdAndReviewerUid(bookId, uid);
    }

    public void upsertReview(Long bookId, SessionUser user, ReviewForm form) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new IllegalArgumentException("Book not found"));
        BookReview review = reviewRepository.findByBookIdAndReviewerUid(bookId, user.uid()).orElseGet(BookReview::new);
        review.setBook(book);
        review.setReviewerUid(user.uid());
        review.setReviewerEmail(user.email());
        review.setReviewerName(user.displayName());
        review.setRating(form.getRating() == null ? 5 : form.getRating());
        review.setText(null);
        reviewRepository.save(review);
        bookService.refreshRating(bookId);
    }

    public void deleteReview(Long bookId, SessionUser user) {
        reviewRepository.deleteByBookIdAndReviewerUid(bookId, user.uid());
        bookService.refreshRating(bookId);
    }
}
