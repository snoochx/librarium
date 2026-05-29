package ru.librarium.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "books", indexes = {
        @Index(name = "idx_books_title", columnList = "title"),
        @Index(name = "idx_books_author", columnList = "author")
})
@Getter
@Setter
@NoArgsConstructor
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String author;

    @Column(columnDefinition = "text")
    private String description;

    private String coverUrl;

    private String genre;

    private Integer publicationYear;

    @Column(nullable = false)
    private boolean featured = false;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private double averageRating = 0.0;

    @Column(nullable = false)
    private int reviewCount = 0;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @jakarta.persistence.Transient
    public String getBadgeLabel() {
        if (averageRating >= 4.8) {
            return "Алмазная";
        }
        if (averageRating >= 4.5) {
            return "Золотая";
        }
        return "Книга";
    }

    @jakarta.persistence.Transient
    public String getBadgeIconPath() {
        if (averageRating >= 4.8) {
            return "/assets/icons/book_diamond.png";
        }
        if (averageRating >= 4.5) {
            return "/assets/icons/book_gold.png";
        }
        return "/assets/icons/book.png";
    }

    @jakarta.persistence.Transient
    public String getBackgroundPath() {
        return "/assets/icons/background.png";
    }
}
