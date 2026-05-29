package ru.librarium.service;

import org.springframework.data.jpa.domain.Specification;
import ru.librarium.entity.Book;

public final class BookSpecifications {
    private BookSpecifications() {}

    public static Specification<Book> visibleOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("visible"));
    }

    public static Specification<Book> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return Specification.where(null);
        }
        String like = "%" + text.toLowerCase().trim() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("author")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("genre")), like)
        );
    }
}
