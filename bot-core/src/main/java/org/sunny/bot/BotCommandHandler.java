package org.sunny.bot;

public interface BotCommandHandler {

    void handle(
            String text,
            String chatId,
            OutboundBot bot
    ) throws Exception;
}
