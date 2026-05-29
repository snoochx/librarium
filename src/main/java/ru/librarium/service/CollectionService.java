package ru.librarium.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.librarium.entity.Book;
import ru.librarium.entity.ReadingStatus;
import ru.librarium.entity.SessionUser;
import ru.librarium.entity.UserBookEntry;
import ru.librarium.repository.BookRepository;
import ru.librarium.repository.UserBookEntryRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CollectionService {
    private final UserBookEntryRepository entryRepository;
    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public List<UserBookEntry> forUser(String uid) {
        return entryRepository.findByUserUidOrderByUpdatedAtDesc(uid);
    }

    @Transactional(readOnly = true)
    public Optional<UserBookEntry> entryForUserBook(String uid, Long bookId) {
        return entryRepository.findByUserUidAndBookId(uid, bookId);
    }

    public void upsertStatus(Long bookId, SessionUser user, ReadingStatus status, String note) {
        if (status == ReadingStatus.NOT_READ) {
            entryRepository.findByUserUidAndBookId(user.uid(), bookId).ifPresent(entryRepository::delete);
            return;
        }

        Book book = bookRepository.findById(bookId).orElseThrow(() -> new IllegalArgumentException("Book not found"));
        UserBookEntry entry = entryRepository.findByUserUidAndBookId(user.uid(), bookId).orElseGet(UserBookEntry::new);
        entry.setUserUid(user.uid());
        entry.setUserEmail(user.email());
        entry.setUserName(user.displayName());
        entry.setBook(book);
        entry.setStatus(status);
        entry.setNote(note);
        entryRepository.save(entry);
    }
}
