package ru.librarium.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookForm {
    @NotBlank
    private String title;

    @NotBlank
    private String author;

    @NotNull(message = "Укажите год публикации")
    @Min(value = 0, message = "Укажите корректный год")
    @Max(value = 3000, message = "Укажите корректный год")
    private Integer publicationYear;

    private String description;
    private String coverUrl;
    private String genre;
    private boolean featured;
    private boolean visible = true;

    private Long suggestionId;
}
