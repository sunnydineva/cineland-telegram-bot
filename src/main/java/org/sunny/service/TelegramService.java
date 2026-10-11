package org.sunny.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunny.config.AppConfig;
import org.sunny.messages.BotMessages;

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
import java.time.format.DateTimeParseException;

public class TelegramService {

    private final String botToken;
    private final String ownerChatId;
    private final String groupChatId;
    private final String botUsername;
    private final Path offsetFile;
    private final HttpClient httpClient;
    private final DateService dateService;
    private final ObjectMapper mapper = new ObjectMapper();

    private long lastUpdateId = 0;
    private CinelandCheckService checkService;

    public TelegramService(
            AppConfig config,
            HttpClient httpClient,
            DateService dateService
    ) {
        this.botToken = config.botToken();
        this.ownerChatId = config.ownerChatId();
        this.groupChatId = config.groupChatId();
        this.botUsername = config.botUsername();
        this.offsetFile = config.offsetFile();
        this.httpClient = httpClient;
        this.dateService = dateService;
        this.lastUpdateId = loadLastUpdateId();
    }

    public void setCheckService(CinelandCheckService checkService) {
        this.checkService = checkService;
    }

    private long loadLastUpdateId() {
        try {
            if (Files.exists(offsetFile)) {
                return Long.parseLong(
                        Files.readString(offsetFile).trim()
                );
            }
        } catch (Exception e) {
            System.err.println(
                    "Cannot load Telegram update offset: "
                            + e.getMessage()
            );
        }

        return 0;
    }

    public void sendStartupMessage(long checkIntervalMinutes)
            throws Exception {
        sendNotification(
                BotMessages.startup(
                        dateService.getSelectedDate(),
                        checkIntervalMinutes
                )
        );
    }

    public void sendNotification(String message) throws Exception {
        sendToChat(ownerChatId, message);

        if (groupChatId != null && !groupChatId.isBlank()) {
            sendToChat(groupChatId, message);
        }
    }

    public void pollCommands() {
        try {
            String url = "https://api.telegram.org/bot"
                    + botToken
                    + "/getUpdates?offset="
                    + (lastUpdateId + 1)
                    + "&timeout=0";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                System.err.println(
                        "Telegram getUpdates HTTP " + response.statusCode()
                );
                return;
            }

            JsonNode root = mapper.readTree(response.body());

            if (!root.path("ok").asBoolean()) {
                System.err.println(
                        "Telegram API error: " + response.body()
                );
                return;
            }

            for (JsonNode update : root.path("result")) {
                processUpdate(update);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println(
                    "Telegram polling error: " + e.getMessage()
            );
        }
    }

    private void processUpdate(JsonNode update) throws Exception {
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
                    ownerChatId.equals(chatId)
                            || (groupChatId != null
                            && groupChatId.equals(chatId));

            boolean allowedUser = ownerChatId.equals(userId);

            if (allowedChat && allowedUser) {
                handleCommand(text, chatId);
            }
        }

        lastUpdateId = Math.max(lastUpdateId, updateId);
        Files.writeString(
                offsetFile,
                Long.toString(lastUpdateId)
        );
    }

    private void handleCommand(
            String text,
            String chatId
    ) throws Exception {
        text = text.replaceFirst(
                "(?i)^(/\\w+)@" + botUsername + "(?=\\s|$)",
                "$1"
        );

        if (text.equals("/start") || text.equals("/help")) {
            sendToChat(chatId, BotMessages.help());

        } else if (text.equals("/status")) {
            sendToChat(
                    chatId,
                    BotMessages.status(dateService.getSelectedDate())
            );

        } else if (text.equals("/date")) {
            sendToChat(chatId, BotMessages.dateUsage());

        } else if (text.startsWith("/date ")) {
            handleDateCommand(text.substring(6).trim(), chatId);

        } else if (text.startsWith("/")) {
            sendToChat(chatId, BotMessages.unknownCommand());
        }
    }

    private void handleDateCommand(
            String dateText,
            String chatId
    ) throws Exception {
        try {
            LocalDate newDate = LocalDate.parse(dateText);
            dateService.saveSelectedDate(newDate);

            sendToChat(
                    chatId,
                    BotMessages.dateChanged(newDate)
            );

            if (checkService != null) {
                checkService.check();
            }

        } catch (DateTimeParseException e) {
            sendToChat(chatId, BotMessages.invalidDate());
        }
    }

    private void sendToChat(String chatId, String message)
            throws Exception {
        String form = "chat_id="
                + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://api.telegram.org/bot"
                                + botToken + "/sendMessage"
                ))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(
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
