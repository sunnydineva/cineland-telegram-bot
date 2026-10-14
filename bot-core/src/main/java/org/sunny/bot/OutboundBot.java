package org.sunny.bot;

public interface OutboundBot {

    void sendNotification(String message) throws Exception;

    void sendToChat(String chatId, String message) throws Exception;
}
