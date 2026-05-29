package ru.librarium.dto;

import ru.librarium.entity.ReadingStatus;

public record ReadingStatusForm(ReadingStatus status, String note) {}
