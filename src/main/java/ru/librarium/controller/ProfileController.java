package ru.librarium.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.librarium.dto.BookSuggestionForm;
import ru.librarium.entity.ReadingStatus;
import ru.librarium.entity.BookReview;
import ru.librarium.entity.SessionUser;
import ru.librarium.entity.UserBookEntry;
import ru.librarium.service.AuthService;
import ru.librarium.service.CollectionService;
import ru.librarium.service.ReviewService;
import ru.librarium.service.SuggestionService;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProfileController {
    private final AuthService authService;
    private final CollectionService collectionService;
    private final ReviewService reviewService;
    private final SuggestionService suggestionService;

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        addProfileModel(model, user);
        model.addAttribute("suggestionForm", new BookSuggestionForm());
        return "profile";
    }

    @GetMapping("/profile/books")
    public String profileBooks(HttpSession session, Model model) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        addProfileModel(model, user);
        return "profile-books";
    }

    @PostMapping(value = "/profile/name", headers = "X-Requested-With=fetch")
    public ResponseEntity<Void> updateNameAjax(@RequestParam String displayName, HttpSession session) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        authService.updateDisplayName(session, displayName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profile/name")
    public String updateName(@RequestParam String displayName,
                             HttpSession session,
                             RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        authService.updateDisplayName(session, displayName);
        ra.addFlashAttribute("success", "Имя обновлено");
        return "redirect:/profile";
    }

    @PostMapping("/profile/books/{bookId}/status")
    public String changeStatus(@PathVariable Long bookId,
                               @RequestParam ReadingStatus status,
                               @RequestParam(required = false) String note,
                               HttpSession session,
                               RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        collectionService.upsertStatus(bookId, user, status, note);
        ra.addFlashAttribute("success", "Статус обновлён");
        return "redirect:/profile";
    }


    @PostMapping("/profile/reviews/{bookId}/delete")
    public String deleteReview(@PathVariable Long bookId,
                               HttpSession session,
                               RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        reviewService.deleteReview(bookId, user);
        ra.addFlashAttribute("success", "Оценка удалена");
        return "redirect:/profile";
    }

    @PostMapping("/profile/suggest")
    public String suggest(@Valid @ModelAttribute BookSuggestionForm form,
                          BindingResult bindingResult,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            addProfileModel(model, user);
            model.addAttribute("suggestionForm", form);
            return "profile";
        }

        suggestionService.submit(user, form);
        ra.addFlashAttribute("success", "Заявка успешно отправлена");
        return "redirect:/profile";
    }

    private void addProfileModel(Model model, SessionUser user) {
        List<UserBookEntry> entries = collectionService.forUser(user.uid());
        List<UserBookEntry> readEntries = entries.stream().filter(e -> e.getStatus() == ReadingStatus.READ).toList();
        List<UserBookEntry> plannedEntries = entries.stream().filter(e -> e.getStatus() == ReadingStatus.PLANNED).toList();

        long readCount = readEntries.size();
        long plannedCount = plannedEntries.size();
        long totalCount = readCount + plannedCount;

        List<BookReview> myReviews = reviewService.reviewsForUser(user.uid());

        model.addAttribute("entries", entries);
        model.addAttribute("readEntries", readEntries);
        model.addAttribute("plannedEntries", plannedEntries);
        model.addAttribute("myReviews", myReviews);
        model.addAttribute("readCount", readCount);
        model.addAttribute("plannedCount", plannedCount);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("profileName", user.displayName());
        model.addAttribute("profileEmail", user.email());
    }
}
