package org.sunny.monitoring;

import org.sunny.bot.TelegramBotConfig;
import org.sunny.bot.TelegramBotService;
import org.sunny.config.AppConfig;
import org.sunny.service.CinelandCheckService;
import org.sunny.service.DateService;
import org.sunny.service.MonitorScheduler;
import org.sunny.service.MonitoringCommandHandler;
import org.sunny.messages.BotMessages;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

public class CinelandMonitor {

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();

        DateService dateService = new DateService(config.initialCheckDate(), config.dateFile());

        LocalDate selectedDate = dateService.loadSelectedDate();

        System.out.println("Selected date: " + selectedDate);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        MonitoringCommandHandler commandHandler = new MonitoringCommandHandler(dateService);

        TelegramBotConfig botConfig = new TelegramBotConfig(
                config.botToken(), config.ownerChatId(), config.groupChatId(), config.botUsername(), config.offsetFile());

        TelegramBotService telegramBotService = new TelegramBotService(
                botConfig, httpClient, commandHandler);

        CinelandCheckService checkService = new CinelandCheckService(config, httpClient, dateService, telegramBotService);

        commandHandler.setCheckService(checkService);

        System.out.println("Cineland Monitor started.");
        System.out.println("Checking every " + config.checkIntervalMinutes() + " minutes...");

        try {
            telegramBotService.sendNotification(BotMessages.startup(dateService.getSelectedDate(), config.checkIntervalMinutes()));
        } catch (Exception e) {
            System.err.println("Telegram error: " + e.getMessage());
        }

        new MonitorScheduler(checkService, telegramBotService, config.checkIntervalMinutes())
                .start();
    }
}
