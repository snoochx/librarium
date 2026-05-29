package ru.librarium.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.librarium.dto.BookForm;
import ru.librarium.entity.Book;
import ru.librarium.entity.BookSuggestion;
import ru.librarium.entity.SessionUser;
import ru.librarium.service.AuthService;
import ru.librarium.service.BookService;
import ru.librarium.service.SuggestionService;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping({"/admin", "/panel"})
public class AdminController {
    private final AuthService authService;
    private final BookService bookService;
    private final SuggestionService suggestionService;

    @GetMapping({"", "/"})
    public String root() {
        return "redirect:/panel/requests";
    }

    @GetMapping("/requests")
    public String requests(HttpSession session, Model model) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }

        List<BookSuggestion> requests = suggestionService.pendingSuggestions();
        model.addAttribute("adminSection", "requests");
        model.addAttribute("requests", requests);
        model.addAttribute("requestsCount", requests.size());
        model.addAttribute("allBooksCount", bookService.visibleCount());
        return "admin-requests";
    }

    @GetMapping("/books")
    public String books(@RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "newest") String sort,
                        HttpSession session,
                        Model model) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }

        List<Book> books = bookService.allAdmin(q, sort);
        model.addAttribute("adminSection", "books");
        model.addAttribute("books", books);
        model.addAttribute("booksCount", books.size());
        model.addAttribute("query", q);
        model.addAttribute("sort", sort);
        return "admin-books";
    }

    @GetMapping({"/books/new", "/book/new"})
    public String createForm(@RequestParam(required = false) Long suggestionId,
                             @RequestParam(required = false) String title,
                             @RequestParam(required = false) String author,
                             @RequestParam(required = false) Integer year,
                             HttpSession session,
                             Model model) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }

        BookForm form = new BookForm();
        form.setSuggestionId(suggestionId);
        form.setTitle(title == null ? "" : title);
        form.setAuthor(author == null ? "" : author);
        form.setPublicationYear(year);

        model.addAttribute("adminSection", "create");
        model.addAttribute("bookForm", form);
        model.addAttribute("editingBook", null);
        model.addAttribute("formMode", "create");
        model.addAttribute("sourceSuggestionId", suggestionId);
        return "admin-book-form";
    }

    @GetMapping("/books/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }

        Book book = bookService.findById(id).orElseThrow(() -> new IllegalArgumentException("Book not found"));
        BookForm form = new BookForm();
        form.setTitle(book.getTitle());
        form.setAuthor(book.getAuthor());
        form.setPublicationYear(book.getPublicationYear());

        model.addAttribute("adminSection", "create");
        model.addAttribute("bookForm", form);
        model.addAttribute("editingBook", book);
        model.addAttribute("formMode", "edit");
        return "admin-book-form";
    }

    @PostMapping("/books")
    public String create(@Valid @ModelAttribute BookForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model,
                         RedirectAttributes ra) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("adminSection", "books");
            model.addAttribute("editingBook", null);
            model.addAttribute("formMode", "create");
            model.addAttribute("sourceSuggestionId", form.getSuggestionId());
            return "admin-book-form";
        }

        bookService.create(form);
        ra.addFlashAttribute("success", "Книга была опубликована");
        return "redirect:/admin/books";
    }

    @PostMapping("/books/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute BookForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model,
                         RedirectAttributes ra) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }

        if (bindingResult.hasErrors()) {
            Book editingBook = bookService.findById(id).orElseThrow(() -> new IllegalArgumentException("Book not found"));
            model.addAttribute("adminSection", "books");
            model.addAttribute("editingBook", editingBook);
            model.addAttribute("formMode", "edit");
            return "admin-book-form";
        }

        bookService.update(id, form);
        ra.addFlashAttribute("success", "Книга обновлена");
        return "redirect:/admin/books";
    }

    @PostMapping("/books/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!ensureAdmin(session)) {
            return "redirect:/";
        }
        bookService.delete(id);
        ra.addFlashAttribute("success", "Книга удалена");
        return "redirect:/admin/books";
    }

    @PostMapping("/suggestions/{id}/approve")
    public String approve(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null || !user.isAdmin()) {
            return "redirect:/";
        }

        BookSuggestion suggestion = suggestionService.approve(id, user);
        UriComponentsBuilder redirectBuilder = UriComponentsBuilder.fromPath("/panel/book/new")
                .queryParam("suggestionId", suggestion.getId())
                .queryParam("title", suggestion.getTitle())
                .queryParam("author", suggestion.getAuthor());

        if (suggestion.getPublicationYear() != null) {
            redirectBuilder.queryParam("year", suggestion.getPublicationYear());
        }

        String redirectUrl = redirectBuilder.build()
                .encode()
                .toUriString();

        return "redirect:" + redirectUrl;
    }

    @PostMapping("/suggestions/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String comment,
                         HttpSession session,
                         RedirectAttributes ra) {
        SessionUser user = authService.currentUser(session).orElse(null);
        if (user == null || !user.isAdmin()) {
            return "redirect:/";
        }
        suggestionService.reject(id, user, comment);
        ra.addFlashAttribute("success", "Заявка была отклонена");
        return "redirect:/panel/requests";
    }

    private boolean ensureAdmin(HttpSession session) {
        return authService.currentUser(session).map(SessionUser::isAdmin).orElse(false);
    }
}
