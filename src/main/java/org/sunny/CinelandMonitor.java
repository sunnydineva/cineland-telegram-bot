package org.sunny;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CinelandMonitor {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static final String BOT_TOKEN =
            DOTENV.get("TELEGRAM_BOT_TOKEN");

    private static final String CHAT_ID =
            DOTENV.get("TELEGRAM_CHAT_ID");

    private static final String GROUP_CHAT_ID =
            DOTENV.get("TELEGRAM_GROUP_CHAT_ID");

    private static final LocalDate CHECK_DATE =
            getDate("CHECK_DATE", LocalDate.of(2026, 10, 16));

    private static final long CHECK_INTERVAL_MINUTES =
            getPositiveLong("CHECK_INTERVAL_MINUTES", 5);

//    private static final String API_URL =
//            "https://restapi.cineland.bg/api/cinema/"
//                    + "0ad469dc-cd3d-4a21-bfed-d7757fe3354d/event"
//                    + "?dateTimeFrom=2026-10-16T00:00:00.000"
//                    + "&dateTimeTo=2026-10-17T23:59:59.999";

    private static final Path STATE =
            Path.of("cineland-response.json");

    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

    public static void main(String[] args) {

        if (BOT_TOKEN == null || BOT_TOKEN.isBlank()
                || CHAT_ID == null || CHAT_ID.isBlank()) {
            throw new IllegalStateException(
                    "Missing TELEGRAM_BOT_TOKEN or "
                            + "TELEGRAM_CHAT_ID in .env"
            );
        }

        System.out.println("Cineland Monitor started.");
        System.out.println(
                "Checking every " + CHECK_INTERVAL_MINUTES
                        + " minutes..."
        );

        try {
            sendTelegram("🟢 Cineland Monitor стартира успешно!\n"
                    + "🎬 Следя прожекциите за " + CHECK_DATE
                    + "\n⏱️ Проверявам на всеки "
                    + CHECK_INTERVAL_MINUTES + " минути.");
        } catch (Exception e) {
            System.err.println("Telegram error: " + e.getMessage());
        }

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        Runtime.getRuntime().addShutdownHook(
                new Thread(scheduler::shutdown)
        );

        scheduler.scheduleWithFixedDelay(
                CinelandMonitor::check,
                0,
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private static long getPositiveLong(String key, long defaultValue) {
        String value = DOTENV.get(key);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(value.trim());

            if (parsed <= 0) {
                throw new IllegalStateException(
                        key + " must be greater than 0"
                );
            }

            return parsed;

        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    key + " must be a whole number",
                    e
            );
        }
    }

    private static LocalDate getDate(String key, LocalDate defaultValue) {
        String value = DOTENV.get(key);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return LocalDate.parse(value.trim());

        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    key + " must use YYYY-MM-DD format",
                    e
            );
        }
    }

    private static String buildApiUrl(LocalDate date) {
        String baseUrl =
                "https://restapi.cineland.bg/api/cinema/"
                        + "0ad469dc-cd3d-4a21-bfed-d7757fe3354d/event";

        return baseUrl
                + "?dateTimeFrom=" + date + "T00:00:00.000"
                + "&dateTimeTo=" + date + "T23:59:59.999";
    }

    private static void check() {

        System.out.println(
                "[" + LocalDateTime.now()
                        + "] Checking Cineland..."
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildApiUrl(CHECK_DATE)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                System.err.println(
                        "Cineland HTTP error: "
                                + response.statusCode()
                );
                return;
            }

            String current = response.body().trim();

            if (current.isEmpty()
                    || current.equals("[]")
                    || current.equals("null")) {

                System.out.println("No events available.");
                return;
            }

            String previous = Files.exists(STATE)
                    ? Files.readString(STATE)
                    : "";

            if (current.equals(previous)) {
                System.out.println("No changes detected.");
                return;
            }

            System.out.println("Changes detected!");

            String message =
                    "🎬 Cineland Варна!\n\n"
                            + "Появиха се нови или променени данни "
                            + "за прожекциите на " + CHECK_DATE + ".\n\n"
                            + "🎟️ https://cineland.bg";

            sendTelegram(message);

            Files.writeString(STATE, current);

            System.out.println(
                    "Telegram notification sent successfully."
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Monitor interrupted.");

        } catch (Exception e) {
            System.err.println(
                    "Monitor error: " + e.getMessage()
            );
        }
    }

    private static void sendTelegram(String message) throws Exception {
        sendToChat(CHAT_ID, message);

        if (GROUP_CHAT_ID != null && !GROUP_CHAT_ID.isBlank()) {
            sendToChat(GROUP_CHAT_ID, message);
        }
    }

    private static void sendToChat(String chatId, String message) throws Exception {
        String form = "chat_id="
                + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://api.telegram.org/bot"
                                + BOT_TOKEN + "/sendMessage"
                ))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Telegram HTTP " + response.statusCode()
                            + ": " + response.body()
            );
        }
    }


}
