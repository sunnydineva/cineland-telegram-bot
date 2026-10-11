package org.sunny.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitorScheduler {

    private final CinelandCheckService checkService;
    private final TelegramService telegramService;
    private final long checkIntervalMinutes;

    public MonitorScheduler(
            CinelandCheckService checkService,
            TelegramService telegramService,
            long checkIntervalMinutes
    ) {
        this.checkService = checkService;
        this.telegramService = telegramService;
        this.checkIntervalMinutes = checkIntervalMinutes;
    }

    public void start() {
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);

        Runtime.getRuntime().addShutdownHook(
                new Thread(scheduler::shutdown)
        );

        scheduler.scheduleWithFixedDelay(
                checkService::check,
                0,
                checkIntervalMinutes,
                TimeUnit.MINUTES
        );

        scheduler.scheduleWithFixedDelay(
                telegramService::pollCommands,
                0,
                3,
                TimeUnit.SECONDS
        );
    }
}
