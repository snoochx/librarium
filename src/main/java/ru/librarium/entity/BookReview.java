package ru.librarium.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "book_reviews",
       uniqueConstraints = @UniqueConstraint(name = "uk_book_review_user_book", columnNames = {"book_id", "reviewer_uid"}))
@Getter
@Setter
@NoArgsConstructor
public class BookReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 128)
    private String reviewerUid;

    @Column(nullable = false)
    private String reviewerEmail;

    @Column(nullable = false)
    private String reviewerName;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "text")
    private String text;

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
}
