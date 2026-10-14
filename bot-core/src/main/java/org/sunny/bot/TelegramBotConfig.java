package org.sunny.bot;

import java.nio.file.Path;

public record TelegramBotConfig(
        String botToken,
        String ownerChatId,
        String groupChatId,
        String botUsername,
        Path offsetFile
) {
}
