package org.sunny;

import io.github.cdimascio.dotenv.Dotenv;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static volatile LocalDate selectedDate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Path DATE_FILE =
            Path.of("cineland-selected-date.txt");

    private static final Path OFFSET_FILE =
            Path.of("telegram-update-offset.txt");

    private static long lastUpdateId = 0;

    private static final String BOT_USERNAME = "sunnydinevabot";

    private static final long CHECK_INTERVAL_MINUTES =
            getPositiveLong("CHECK_INTERVAL_MINUTES", 5);

    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

    public static void main(String[] args) {

        selectedDate = getDate(
                "CHECK_DATE",
                LocalDate.of(2026, 10, 16)
        );

        try {
            if (Files.exists(DATE_FILE)) {
                selectedDate = LocalDate.parse(
                        Files.readString(DATE_FILE).trim()
                );
            }
        } catch (Exception e) {
            System.err.println(
                    "Cannot load saved date: " + e.getMessage()
            );
        }

        System.out.println("Selected date: " + selectedDate);

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
                    + "🎬 Следя прожекциите за " + selectedDate
                    + "\n⏱️ Проверявам на всеки "
                    + CHECK_INTERVAL_MINUTES + " минути.");
        } catch (Exception e) {
            System.err.println("Telegram error: " + e.getMessage());
        }

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);


        Runtime.getRuntime().addShutdownHook(
                new Thread(scheduler::shutdown)
        );

        scheduler.scheduleWithFixedDelay(
                CinelandMonitor::check,
                0,
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        scheduler.scheduleWithFixedDelay(
                CinelandMonitor::checkTelegramCommands,
                0,
                3,
                TimeUnit.SECONDS
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
                        + "0ad469dc-cd3d-4a21-bfed-d7757fe3354d/screening";

        return baseUrl
                + "?dateTimeFrom=" + date + "T00:00:00.000"
                + "&dateTimeTo=" + date.plusDays(1) + "T23:59:59.999";
    }

    private static void checkTelegramCommands() {

        try {
            String url = "https://api.telegram.org/bot"
                    + BOT_TOKEN
                    + "/getUpdates?offset="
                    + (lastUpdateId + 1)
                    + "&timeout=0";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                System.err.println(
                        "Telegram getUpdates HTTP " + response.statusCode()
                );
                return;
            }

            JsonNode root = MAPPER.readTree(response.body());

            if (!root.path("ok").asBoolean()) {
                System.err.println(
                        "Telegram API error: " + response.body()
                );
                return;
            }

            for (JsonNode update : root.path("result")) {

                long updateId = update.path("update_id").asLong();

                JsonNode message = update.path("message");

                if (!message.isMissingNode()) {

                    String chatId = message.path("chat")
                            .path("id").asText();

                    String userId = message.path("from")
                            .path("id").asText();

                    String text = message.path("text")
                            .asText("").trim();

                    boolean allowedChat =
                            CHAT_ID.equals(chatId)
                                    || (GROUP_CHAT_ID != null
                                    && GROUP_CHAT_ID.equals(chatId));

                    // Само собственикът може да управлява бота.
                    boolean allowedUser = CHAT_ID.equals(userId);

                    if (allowedChat && allowedUser) {
                        handleCommand(text, chatId);
                    }
                }

                // Запомняме кои съобщения вече са обработени.
                lastUpdateId = Math.max(lastUpdateId, updateId);

                Files.writeString(
                        OFFSET_FILE,
                        Long.toString(lastUpdateId)
                );
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println(
                    "Telegram polling error: " + e.getMessage()
            );
        }
    }

    private static void handleCommand(
            String text,
            String chatId
    ) throws Exception {

        // Поддържаме и /date@sunnydinevabot в групата.
        text = text.replaceFirst(
                "(?i)^(/\\w+)@" + BOT_USERNAME + "(?=\\s|$)",
                "$1"
        );

        if (text.equals("/start") || text.equals("/help")) {

            sendToChat(
                    chatId,
                    "🎬 Cineland Monitor\n\n"
                            + "/date 2026-10-16 — избери дата\n"
                            + "/status — текуща дата\n"
                            + "/help — помощ"
            );

        } else if (text.equals("/status")) {

            sendToChat(
                    chatId,
                    "🎬 В момента следя: " + selectedDate
            );

        } else if (text.equals("/date")) {

            sendToChat(
                    chatId,
                    "📅 Изпрати например:\n/date 2026-10-16"
            );

        } else if (text.startsWith("/date ")) {

            String dateText = text.substring(6).trim();

            try {
                LocalDate newDate = LocalDate.parse(dateText);

                // Първо запазваме датата на диска.
                Files.writeString(
                        DATE_FILE,
                        newDate.toString()
                );

                selectedDate = newDate;

                sendToChat(
                        chatId,
                        "✅ Започвам да следя прожекциите за "
                                + newDate
                );

                // Проверяваме веднага след командата.
                check();

            } catch (DateTimeParseException e) {

                sendToChat(
                        chatId,
                        "❌ Невалидна дата.\n"
                                + "Пример: /date 2026-10-16"
                );
            }

        } else if (text.startsWith("/")) {

            sendToChat(
                    chatId,
                    "❓ Непозната команда. Изпрати /help."
            );
        }
    }

    private static void check() {

        LocalDate date = selectedDate;

        System.out.println(
                "[" + LocalDateTime.now()
                        + "] Checking Cineland..."
        );

        Path stateFile =
                Path.of("cineland-response-" + date + ".json");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildApiUrl(date)))
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

            String previous = Files.exists(stateFile)
                    ? Files.readString(stateFile)
                    : "";

            if (current.equals(previous)) {
                System.out.println("No changes detected.");
                return;
            }

            System.out.println("Changes detected!");

            String message =
                    "🎬 Cineland Варна!\n\n"
                            + "Появиха се нови или променени данни "
                            + "за прожекциите на " + date + ".\n\n"
                            + "🎟️ https://cineland.bg";

            sendTelegram(message);

            Files.writeString(stateFile, current);

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
