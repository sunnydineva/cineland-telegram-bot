package org.sunny.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class DateService {

    private final Path dateFile;
    private volatile LocalDate selectedDate;

    public DateService(LocalDate initialDate, Path dateFile) {
        this.selectedDate = initialDate;
        this.dateFile = dateFile;
    }

    public LocalDate loadSelectedDate() {
        try {
            if (Files.exists(dateFile)) {
                selectedDate = LocalDate.parse(Files.readString(dateFile).trim());
            }
        } catch (Exception e) {
            System.err.println("Cannot load saved date: " + e.getMessage());
        }

        return selectedDate;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void saveSelectedDate(LocalDate newDate) throws Exception {
        Files.writeString(dateFile, newDate.toString());
        selectedDate = newDate;
    }
}
