package ru.librarium.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.librarium.dto.BookForm;
import ru.librarium.entity.Book;
import ru.librarium.entity.BookReview;
import ru.librarium.repository.BookRepository;
import ru.librarium.repository.BookReviewRepository;
import ru.librarium.repository.UserBookEntryRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookService {
    private final BookRepository bookRepository;
    private final BookReviewRepository reviewRepository;
    private final UserBookEntryRepository entryRepository;

    @Transactional(readOnly = true)
    public Page<Book> search(String q, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, sortFor(sort));
        Specification<Book> spec = Specification.where(BookSpecifications.visibleOnly())
                .and(BookSpecifications.matchesText(q));
        return bookRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<Book> featured() {
        Pageable pageable = PageRequest.of(0, 6, Sort.by(Sort.Order.desc("averageRating"), Sort.Order.desc("reviewCount"), Sort.Order.desc("createdAt")));
        Specification<Book> spec = Specification.where(BookSpecifications.visibleOnly());
        return bookRepository.findAll(spec, pageable).getContent();
    }

    @Transactional(readOnly = true)
    public List<Book> allAdmin(String q, String sort) {
        Specification<Book> spec = Specification.where(BookSpecifications.matchesText(q));
        return bookRepository.findAll(spec, sortFor(sort));
    }

    @Transactional(readOnly = true)
    public long visibleCount() {
        return bookRepository.count((root, query, cb) -> cb.isTrue(root.get("visible")));
    }

    @Transactional(readOnly = true)
    public String friendlyCount(long count) {
        if (count < 100) {
            return String.valueOf(count);
        }
        if (count < 1000) {
            long rounded = Math.round(count / 100.0) * 100;
            return String.valueOf(Math.max(100, rounded));
        }
        if (count < 10000) {
            long rounded = Math.round(count / 1000.0) * 1000;
            return String.valueOf(Math.max(1000, rounded));
        }
        long rounded = Math.round(count / 5000.0) * 5000;
        return String.valueOf(Math.max(5000, rounded));
    }

    @Transactional(readOnly = true)
    public Optional<Book> findById(Long id) {
        return bookRepository.findById(id);
    }

    public Book create(BookForm form) {
        Book book = new Book();
        copy(form, book);
        return bookRepository.save(book);
    }

    public Book update(Long id, BookForm form) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Book not found"));
        copy(form, book);
        return bookRepository.save(book);
    }

    public void delete(Long id) {
        entryRepository.deleteByBookId(id);
        reviewRepository.deleteByBookId(id);
        bookRepository.deleteById(id);
    }

    public void refreshRating(Long bookId) {
        Book book = bookRepository.findById(bookId).orElseThrow();
        List<BookReview> reviews = reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId);
        int count = reviews.size();
        double avg = reviews.stream().mapToInt(BookReview::getRating).average().orElse(0.0);
        book.setReviewCount(count);
        book.setAverageRating(Math.round(avg * 100.0) / 100.0);
        bookRepository.save(book);
    }

    public void rebuildRatings() {
        for (Book book : bookRepository.findAll()) {
            List<BookReview> reviews = reviewRepository.findByBookIdOrderByCreatedAtDesc(book.getId());
            int count = reviews.size();
            double avg = reviews.stream().mapToInt(BookReview::getRating).average().orElse(0.0);
            book.setReviewCount(count);
            book.setAverageRating(Math.round(avg * 100.0) / 100.0);
            bookRepository.save(book);
        }
    }

    private void copy(BookForm form, Book book) {
        book.setTitle(form.getTitle().trim());
        book.setAuthor(form.getAuthor().trim());
        book.setDescription(form.getDescription());
        book.setCoverUrl(form.getCoverUrl());
        book.setGenre(form.getGenre());
        book.setPublicationYear(form.getPublicationYear());
        book.setFeatured(form.isFeatured());
        book.setVisible(form.isVisible());
    }

    private Sort sortFor(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }
        return switch (sort) {
            case "title" -> Sort.by(Sort.Order.asc("title"));
            case "author" -> Sort.by(Sort.Order.asc("author"));
            case "rating" -> Sort.by(Sort.Order.desc("averageRating"), Sort.Order.desc("reviewCount"));
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };
    }
}
