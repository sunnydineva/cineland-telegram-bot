package org.sunny.service;

import org.sunny.bot.BotCommandHandler;
import org.sunny.bot.OutboundBot;
import org.sunny.messages.BotMessages;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class MonitoringCommandHandler implements BotCommandHandler {

    private final DateService dateService;
    private CinelandCheckService checkService;

    public MonitoringCommandHandler(DateService dateService) {
        this.dateService = dateService;
    }

    public void setCheckService(CinelandCheckService checkService) {
        this.checkService = checkService;
    }

    @Override
    public void handle(String text, String chatId, OutboundBot bot) throws Exception {
        if (text.equals("/start") || text.equals("/help")) {
            bot.sendToChat(chatId, BotMessages.help());

        } else if (text.equals("/status")) {
            bot.sendToChat(chatId, BotMessages.status(dateService.getSelectedDate())
            );

        } else if (text.equals("/date")) {
            bot.sendToChat(chatId, BotMessages.dateUsage());

        } else if (text.startsWith("/date ")) {
            handleDateCommand(text.substring(6).trim(), chatId, bot);

        } else if (text.startsWith("/")) {
            bot.sendToChat(chatId, BotMessages.unknownCommand());
        }
    }

    private void handleDateCommand(String dateText, String chatId, OutboundBot bot) throws Exception {
        try {
            LocalDate newDate = LocalDate.parse(dateText);
            dateService.saveSelectedDate(newDate);

            bot.sendToChat(chatId, BotMessages.dateChanged(newDate)
            );

            if (checkService != null) {
                checkService.check();
            }

        } catch (DateTimeParseException e) {
            bot.sendToChat(chatId, BotMessages.invalidDate());
        }
    }
}
