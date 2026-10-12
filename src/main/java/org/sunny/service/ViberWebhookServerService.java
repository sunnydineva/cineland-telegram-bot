package org.sunny.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.sunny.config.AppConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ViberWebhookServerService {

    private final int port;
    private final String path;
    private final String publicUrl;

    public ViberWebhookServerService(AppConfig config) {
        this.port = config.viberWebhookPort();
        this.path = config.viberWebhookPath();
        this.publicUrl = "https://"
                + config.viberWebhookDomain()
                + config.viberWebhookPath();
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(port),
                    0
            );
            server.createContext(path, this::handleWebhook);
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> server.stop(2))
            );

            System.out.println(
                    "Viber webhook server started on port "
                            + port
                            + " at POST "
                            + path
            );
            System.out.println("Viber public webhook URL: " + publicUrl);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot start Viber webhook server on port " + port,
                    e
            );
        }
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String payload = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        System.out.println("Viber webhook payload: " + payload);

        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String response
    ) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8"
        );
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
