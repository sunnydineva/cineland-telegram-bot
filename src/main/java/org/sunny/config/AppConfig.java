package org.sunny.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record AppConfig(
        String botToken,
        String ownerChatId,
        String groupChatId,
        String botUsername,
        long checkIntervalMinutes,
        LocalDate initialCheckDate,
        Path dateFile,
        Path offsetFile,
        String cinelandBaseUrl,
        String cinelandResourceType,
        String cinelandResourceId,
        String cinelandEndpointPath,
        String cinelandMovieId,
        String viberWebhookDomain,
        int viberWebhookPort,
        String viberWebhookPath
) {

    private static final LocalDate DEFAULT_CHECK_DATE =
            LocalDate.of(2026, 10, 16);
    private static final ObjectMapper YAML_MAPPER =
            new ObjectMapper(new YAMLFactory());

    public static AppConfig load() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        JsonNode yaml = loadApplicationYaml();

        String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        String ownerChatId = dotenv.get("TELEGRAM_CHAT_ID");

        if (isBlank(botToken) || isBlank(ownerChatId)) {
            throw new IllegalStateException(
                    "Missing TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID in .env"
            );
        }

        return new AppConfig(
                botToken,
                ownerChatId,
                dotenv.get("TELEGRAM_GROUP_CHAT_ID"),
                getString(
                        dotenv,
                        "TELEGRAM_BOT_USERNAME",
                        yamlText(yaml, "telegram", "bot-username")
                ),
                getPositiveLong(dotenv, "CHECK_INTERVAL_MINUTES", 5),
                getDate(dotenv, "CHECK_DATE", DEFAULT_CHECK_DATE),
                Path.of(yamlText(yaml, "state", "date-file")),
                Path.of(yamlText(yaml, "state", "offset-file")),
                yamlText(yaml, "cineland", "base-url"),
                yamlText(yaml, "cineland", "resource-type"),
                yamlText(yaml, "cineland", "resource-id"),
                yamlText(yaml, "cineland", "endpoint-path"),
                yamlTextOrDefault(yaml, "cineland", "movie-id", "null"),
                getString(
                        dotenv,
                        "VIBER_WEBHOOK_DOMAIN",
                        yamlText(yaml, "viber", "webhook", "domain")
                ),
                Math.toIntExact(
                        getPositiveLong(
                                dotenv,
                                "VIBER_WEBHOOK_PORT",
                                yamlLong(yaml, "viber", "webhook", "port")
                        )
                ),
                normalizeWebhookPath(
                        getString(
                                dotenv,
                                "VIBER_WEBHOOK_PATH",
                                yamlText(yaml, "viber", "webhook", "path")
                        )
                )
        );
    }

    private static JsonNode loadApplicationYaml() {
        try (InputStream input = AppConfig.class.getResourceAsStream(
                "/application.yml"
        )) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing application.yml on classpath"
                );
            }

            return YAML_MAPPER.readTree(input);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot load application.yml",
                    e
            );
        }
    }

    private static String yamlText(
            JsonNode yaml,
            String section,
            String key
    ) {
        String value = yaml.path(section).path(key).asText("").trim();

        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Missing " + section + "." + key
                            + " in application.yml"
            );
        }

        return value;
    }

    private static String yamlText(
            JsonNode yaml,
            String section,
            String subsection,
            String key
    ) {
        String value = yaml.path(section)
                .path(subsection)
                .path(key)
                .asText("")
                .trim();

        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Missing " + section + "."
                            + subsection + "."
                            + key
                            + " in application.yml"
            );
        }

        return value;
    }

    private static long yamlLong(
            JsonNode yaml,
            String section,
            String subsection,
            String key
    ) {
        JsonNode node = yaml.path(section).path(subsection).path(key);

        if (!node.canConvertToLong()) {
            throw new IllegalStateException(
                    "Missing or invalid " + section + "."
                            + subsection + "."
                            + key
                            + " in application.yml"
            );
        }

        long value = node.asLong();

        if (value <= 0) {
            throw new IllegalStateException(
                    section + "." + subsection + "." + key
                            + " must be greater than 0"
            );
        }

        return value;
    }

    private static String yamlTextOrDefault(
            JsonNode yaml,
            String section,
            String key,
            String defaultValue
    ) {
        JsonNode node = yaml.path(section).path(key);

        if (node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }

        String value = node.asText("").trim();
        return value.isBlank() ? defaultValue : value;
    }

    private static String getString(
            Dotenv dotenv,
            String key,
            String defaultValue
    ) {
        String value = dotenv.get(key);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String normalizeWebhookPath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static long getPositiveLong(
            Dotenv dotenv,
            String key,
            long defaultValue
    ) {
        String value = dotenv.get(key);

        if (isBlank(value)) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(value.trim());

            if (parsed <= 0) {
                throw new IllegalStateException(
                        key + " must be greater than 0"
                );
            }

            return parsed;

        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    key + " must be a whole number",
                    e
            );
        }
    }

    private static LocalDate getDate(
            Dotenv dotenv,
            String key,
            LocalDate defaultValue
    ) {
        String value = dotenv.get(key);

        if (isBlank(value)) {
            return defaultValue;
        }

        try {
            return LocalDate.parse(value.trim());

        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    key + " must use YYYY-MM-DD format",
                    e
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
