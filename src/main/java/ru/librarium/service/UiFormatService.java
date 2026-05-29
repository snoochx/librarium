package ru.librarium.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("uiFormat")
public class UiFormatService {
    private static final Locale RU = new Locale("ru");
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter REVIEW_DATE =
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", RU);

    public String ratingPercent(double value) {
        double safe = Math.max(0.0, Math.min(5.0, value));
        return String.format(Locale.US, "%.1f", (safe / 5.0) * 100.0);
    }

    public String averageRatingLabel(double value) {
        double safe = Math.max(0.0, Math.min(5.0, value));
        return String.format(Locale.US, "%.1f", safe);
    }

    public String reviewRatingLabel(double value) {
        int safe = (int) Math.round(Math.max(0.0, Math.min(5.0, value)));
        return String.valueOf(safe);
    }

    public String reviewDate(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return REVIEW_DATE.format(instant.atZone(DISPLAY_ZONE));
    }

    public String reviewCount(int count) {
        int mod100 = count % 100;
        if (mod100 >= 11 && mod100 <= 14) {
            return count + " отзывов";
        }
        int mod10 = count % 10;
        return switch (mod10) {
            case 1 -> count + " отзыв";
            case 2, 3, 4 -> count + " отзыва";
            default -> count + " отзывов";
        };
    }

    public String starFillPercent(double value, int starIndex) {
        double safe = Math.max(0.0, Math.min(5.0, value));
        double fill = Math.max(0.0, Math.min(1.0, safe - (starIndex - 1)));
        return String.format(Locale.US, "%.1f", fill * 100.0);
    }

    public String bookStatusLabel(ru.librarium.entity.ReadingStatus status) {
        return status == null ? "" : status.getLabel();
    }
}
