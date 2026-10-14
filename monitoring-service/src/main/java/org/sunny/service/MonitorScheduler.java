package org.sunny.service;

import org.sunny.bot.TelegramBotService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitorScheduler {

    private final CinelandCheckService checkService;
    private final TelegramBotService telegramBotService;
    private final long checkIntervalMinutes;

    public MonitorScheduler(CinelandCheckService checkService, TelegramBotService telegramBotService, long checkIntervalMinutes
    ) {
        this.checkService = checkService;
        this.telegramBotService = telegramBotService;
        this.checkIntervalMinutes = checkIntervalMinutes;
    }

    public void start() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));

        scheduler.scheduleWithFixedDelay(checkService::check, 0, checkIntervalMinutes, TimeUnit.MINUTES);

        scheduler.scheduleWithFixedDelay(telegramBotService::pollCommands, 0, 3, TimeUnit.SECONDS);
    }
}
