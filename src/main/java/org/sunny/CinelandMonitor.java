package org.sunny;

import org.sunny.config.AppConfig;
import org.sunny.service.CinelandCheckService;
import org.sunny.service.DateService;
import org.sunny.service.MonitorScheduler;
import org.sunny.service.TelegramService;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

public class CinelandMonitor {

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();

        DateService dateService = new DateService(
                config.initialCheckDate(),
                config.dateFile()
        );
        LocalDate selectedDate = dateService.loadSelectedDate();

        System.out.println("Selected date: " + selectedDate);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        TelegramService telegramService = new TelegramService(
                config,
                httpClient,
                dateService
        );

        CinelandCheckService checkService = new CinelandCheckService(
                config,
                httpClient,
                dateService,
                telegramService
        );
        telegramService.setCheckService(checkService);

        System.out.println("Cineland Monitor started.");
        System.out.println(
                "Checking every " + config.checkIntervalMinutes()
                        + " minutes..."
        );

        try {
            telegramService.sendStartupMessage(
                    config.checkIntervalMinutes()
            );
        } catch (Exception e) {
            System.err.println("Telegram error: " + e.getMessage());
        }

        new MonitorScheduler(
                checkService,
                telegramService,
                config.checkIntervalMinutes()
        ).start();
    }
}
