package org.sunny.service;

import org.sunny.config.AppConfig;
import org.sunny.messages.BotMessages;
import org.sunny.bot.OutboundBot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CinelandCheckService {

    private final HttpClient httpClient;
    private final DateService dateService;
    private final OutboundBot bot;
    private final String baseUrl;
    private final String resourceType;
    private final String resourceId;
    private final String endpointPath;
    private final String movieId;

    public CinelandCheckService(
            AppConfig config,
            HttpClient httpClient,
            DateService dateService,
            OutboundBot bot
    ) {
        this.httpClient = httpClient;
        this.dateService = dateService;
        this.bot = bot;
        this.baseUrl = stripTrailingSlash(config.cinelandBaseUrl());
        this.resourceType = stripSlashes(config.cinelandResourceType());
        this.resourceId = config.cinelandResourceId();
        this.endpointPath = stripSlashes(config.cinelandEndpointPath());
        this.movieId = config.cinelandMovieId();
    }

    public void check() {
        LocalDate date = dateService.getSelectedDate();

        System.out.println("[" + LocalDateTime.now() + "] Checking Cineland...");

        Path stateFile = Path.of("cineland-response-" + date + ".json");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildApiUrl(date)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Cineland HTTP error: " + response.statusCode());
                return;
            }

            String current = response.body().trim();

            if (current.isEmpty()
                    || current.equals("[]")
                    || current.equals("null")) {

                System.out.println("No events available.");
                return;
            }

            String previous = Files.exists(stateFile) ? Files.readString(stateFile) : "";

            if (current.equals(previous)) {
                System.out.println("No changes detected.");
                return;
            }

            System.out.println("Changes detected!");

            bot.sendNotification(BotMessages.cinelandChanged(date));

            Files.writeString(stateFile, current);

            System.out.println("Telegram notification sent successfully.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Monitor interrupted.");

        } catch (Exception e) {
            System.err.println("Monitor error: " + e.getMessage());
        }
    }

    private String buildApiUrl(LocalDate date) {
        return baseUrl + "/" + resourceType + "/" + resourceId + "/" + endpointPath
                + "?dateTimeFrom=" + date + "T00:00:00.000"
                + "&dateTimeTo=" + date.plusDays(1) + "T23:59:59.999"
                + "&movieId=" + movieId;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String stripSlashes(String value) {
        String result = value;

        while (result.startsWith("/")) {
            result = result.substring(1);
        }

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }
}
