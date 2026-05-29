package ru.librarium.entity;

public enum ReadingStatus {
    NOT_READ("Не читал"),
    PLANNED("В планах"),
    IN_PROGRESS("Читаю"),
    READ("Прочитано"),
    DROPPED("Брошено");

    private final String label;

    ReadingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
