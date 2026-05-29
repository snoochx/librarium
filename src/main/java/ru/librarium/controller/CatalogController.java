package ru.librarium.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.librarium.dto.ReviewForm;
import ru.librarium.entity.Book;
import ru.librarium.entity.BookReview;
import ru.librarium.entity.ReadingStatus;
import ru.librarium.entity.SessionUser;
import ru.librarium.entity.UserBookEntry;
import ru.librarium.service.AuthService;
import ru.librarium.service.BookService;
import ru.librarium.service.CollectionService;
import ru.librarium.service.ReviewService;

import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class CatalogController {
    private final BookService bookService;
    private final ReviewService reviewService;
    private final CollectionService collectionService;
    private final AuthService authService;

    @GetMapping("/catalog")
    public String catalog(@RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "newest") String sort,
                          @RequestParam(defaultValue = "0") int page,
                          HttpSession session,
                          Model model) {
        Page<Book> books = bookService.search(q, sort, page, 12);
        long totalBooks = bookService.visibleCount();

        SessionUser currentUser = authService.currentUser(session).orElse(null);
        Map<Long, ReadingStatus> bookStatusById = Map.of();
        if (currentUser != null) {
            bookStatusById = collectionService.forUser(currentUser.uid()).stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getBook().getId(),
                            UserBookEntry::getStatus,
                            (left, right) -> left
                    ));
        }

        model.addAttribute("books", books);
        model.addAttribute("query", q);
        model.addAttribute("sort", sort);
        model.addAttribute("totalBooks", totalBooks);
        model.addAttribute("totalBooksLabel", bookService.friendlyCount(totalBooks));
        model.addAttribute("bookStatusById", bookStatusById);
        return "catalog";
    }

    @GetMapping("/books/{id}")
    public String book(@PathVariable Long id, Model model, HttpSession session) {
        populateBookPage(model, id, session, new ReviewForm(), false);
        return "book";
    }

    @PostMapping("/books/{id}/review")
    public String review(@PathVariable Long id,
                         @Valid @ModelAttribute ReviewForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model,
                         RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            populateBookPage(model, id, session, form, true);
            return "book";
        }

        reviewService.upsertReview(id, user, form);
        ra.addFlashAttribute("success", "Оценка сохранена");
        return "redirect:/books/" + id;
    }

    @PostMapping("/books/{id}/review/delete")
    public String deleteReview(@PathVariable Long id,
                               HttpSession session,
                               RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        reviewService.deleteReview(id, user);
        ra.addFlashAttribute("success", "Оценка удалена");
        return "redirect:/books/" + id;
    }

    @PostMapping("/books/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam ReadingStatus status,
                               @RequestParam(required = false) String note,
                               HttpSession session,
                               RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        collectionService.upsertStatus(id, user, status, note);
        ra.addFlashAttribute("success", "Статус обновлён");
        return "redirect:/books/" + id;
    }

    private void populateBookPage(Model model, Long id, HttpSession session, ReviewForm reviewForm, boolean preserveFormValues) {
        Book book = bookService.findById(id).orElseThrow(() -> new IllegalArgumentException("Book not found"));
        SessionUser currentUser = authService.currentUser(session).orElse(null);

        model.addAttribute("book", book);
        model.addAttribute("reviews", reviewService.reviewsForBook(id));
        model.addAttribute("reviewForm", reviewForm);

        if (currentUser != null) {
            UserBookEntry entry = collectionService.entryForUserBook(currentUser.uid(), id).orElse(null);
            BookReview myReview = reviewService.reviewForUser(id, currentUser.uid()).orElse(null);
            model.addAttribute("userEntry", entry);
            model.addAttribute("myReview", myReview);

            if (myReview != null && !preserveFormValues) {
                reviewForm.setRating(myReview.getRating());
            }
        } else {
            model.addAttribute("userEntry", null);
            model.addAttribute("myReview", null);
        }
    }
}
