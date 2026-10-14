package org.sunny.bot;

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

public class TelegramBotService implements OutboundBot {

    private final String botToken;
    private final String ownerChatId;
    private final String groupChatId;
    private final String botUsername;
    private final Path offsetFile;
    private final HttpClient httpClient;
    private final BotCommandHandler commandHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    private long lastUpdateId = 0;

    public TelegramBotService(
            TelegramBotConfig config,
            HttpClient httpClient,
            BotCommandHandler commandHandler
    ) {
        this.botToken = config.botToken();
        this.ownerChatId = config.ownerChatId();
        this.groupChatId = config.groupChatId();
        this.botUsername = config.botUsername();
        this.offsetFile = config.offsetFile();
        this.httpClient = httpClient;
        this.commandHandler = commandHandler;
        this.lastUpdateId = loadLastUpdateId();
    }

    private long loadLastUpdateId() {
        try {
            if (Files.exists(offsetFile)) {
                return Long.parseLong(Files.readString(offsetFile).trim());
            }
        } catch (Exception e) {
            System.err.println("Cannot load Telegram update offset: " + e.getMessage()
            );
        }

        return 0;
    }

    @Override
    public void sendNotification(String message) throws Exception {
        sendToChat(ownerChatId, message);

        if (groupChatId != null && !groupChatId.isBlank()) {
            sendToChat(groupChatId, message);
        }
    }

    public void pollCommands() {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=0";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Telegram getUpdates HTTP " + response.statusCode());
                return;
            }

            JsonNode root = mapper.readTree(response.body());

            if (!root.path("ok").asBoolean()) {
                System.err.println("Telegram API error: " + response.body());
                return;
            }

            for (JsonNode update : root.path("result")) {
                processUpdate(update);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Telegram polling error: " + e.getMessage());
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

            boolean allowedChat = ownerChatId.equals(chatId)
                            || (groupChatId != null
                            && groupChatId.equals(chatId));

            boolean allowedUser = ownerChatId.equals(userId);

            if (allowedChat && allowedUser) {
                commandHandler.handle(normalizeCommand(text), chatId, this);
            }
        }

        lastUpdateId = Math.max(lastUpdateId, updateId);
        Files.writeString(offsetFile, Long.toString(lastUpdateId));
    }

    private String normalizeCommand(String text) {
        return text.replaceFirst("(?i)^(/\\w+)@" + botUsername + "(?=\\s|$)", "$1");
    }

    @Override
    public void sendToChat(String chatId, String message)
            throws Exception {
        String form =
                "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Telegram HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
