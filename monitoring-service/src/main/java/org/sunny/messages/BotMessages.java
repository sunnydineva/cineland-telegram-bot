package org.sunny.messages;

import java.time.LocalDate;

public final class BotMessages {

    private BotMessages() {
    }

    public static String startup(
            LocalDate selectedDate,
            long checkIntervalMinutes
    ) {
        return "🟢 Cineland Monitor стартира успешно!\n"
                + "🎬 Следя прожекциите за " + selectedDate
                + "\n⏱️ Проверявам на всеки "
                + checkIntervalMinutes + " минути.";
    }

    public static String help() {
        return "🎬 Cineland Monitor\n\n"
                + "/date 2026-10-16 — избери дата\n"
                + "/status — текуща дата\n"
                + "/help — помощ";
    }

    public static String status(LocalDate selectedDate) {
        return "🎬 В момента следя: " + selectedDate;
    }

    public static String dateUsage() {
        return "📅 Изпрати например:\n/date 2026-10-16";
    }

    public static String dateChanged(LocalDate selectedDate) {
        return "✅ Започвам да следя прожекциите за "
                + selectedDate;
    }

    public static String invalidDate() {
        return "❌ Невалидна дата.\n"
                + "Пример: /date 2026-10-16";
    }

    public static String unknownCommand() {
        return "❓ Непозната команда. Изпрати /help.";
    }

    public static String cinelandChanged(LocalDate date) {
        return "🎬 Cineland Варна!\n\n"
                + "Появиха се нови или променени данни "
                + "за прожекциите на " + date + ".\n\n"
                + "🎟️ https://cineland.bg";
    }
}
