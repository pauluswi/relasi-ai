package com.pswied.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Starts the dependency-free, loopback-only RELASI AI showcase shell. */
public final class RelasiAiApplication {
    private static final String API_PREFIX = "/api/v1";

    private RelasiAiApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = configuredPort();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", RelasiAiApplication::handle);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }, "relasi-ai-shutdown"));

        System.out.printf("RELASI AI local mock shell started at http://127.0.0.1:%d%n", port);
        System.out.println("No database, Docker, network connection, or credentials are used.");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int configuredPort() {
        String value = System.getenv().getOrDefault("RELASI_PORT", "8080");
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("RELASI_PORT must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("RELASI_PORT must be a valid port number.");
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || "/index.html".equals(path)) {
            serveIndex(exchange);
            return;
        }

        if (!path.startsWith(API_PREFIX)) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"method_not_allowed\"}");
            return;
        }

        switch (path) {
            case API_PREFIX + "/health" -> send(exchange, 200, "application/json; charset=utf-8",
                    "{\"status\":\"UP\",\"mode\":\"mock\",\"storage\":\"in-memory\"}");
            case API_PREFIX + "/demo-users" -> send(exchange, 200, "application/json; charset=utf-8",
                    DemoUserDirectory.publicUsersJson());
            case API_PREFIX + "/session" -> serveSession(exchange);
            default -> send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
        }
    }

    private static void serveSession(HttpExchange exchange) throws IOException {
        String requestedUser = exchange.getRequestHeaders().getFirst("X-Demo-User");
        DemoUser user = DemoUserDirectory.find(requestedUser);
        if (user == null) {
            send(exchange, 401, "application/json; charset=utf-8",
                    "{\"error\":\"demo_user_required\",\"message\":\"Select a seeded demo user using X-Demo-User.\"}");
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", user.sessionJson());
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        try (var input = RelasiAiApplication.class.getResourceAsStream("/static/index.html")) {
            if (input == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "UI resource not found");
                return;
            }
            send(exchange, 200, "text/html; charset=utf-8", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

record DemoUser(String id, String displayName, String role, List<String> customerScope) {
    String publicJson() {
        return "{\"id\":" + quote(id) + ",\"displayName\":" + quote(displayName)
                + ",\"role\":" + quote(role) + "}";
    }

    String sessionJson() {
        String customers = customerScope.stream().map(DemoUser::quote).collect(java.util.stream.Collectors.joining(","));
        return "{\"user\":" + publicJson() + ",\"customerScope\":[" + customers + "],\"authentication\":\"local-demo\"}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

final class DemoUserDirectory {
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("service-officer", "Service Officer", "CUSTOMER_SERVICE_OFFICER", List.of("CUST-1001", "CUST-1002")),
            new DemoUser("relationship-manager", "Relationship Manager", "RELATIONSHIP_MANAGER", List.of("CUST-1003", "CUST-1004")),
            new DemoUser("supervisor", "CRM Supervisor", "CRM_SUPERVISOR", List.of("CUST-1001", "CUST-1002", "CUST-1003", "CUST-1004")),
            new DemoUser("administrator", "CRM Administrator", "CRM_ADMINISTRATOR", List.of("CUST-1001", "CUST-1002", "CUST-1003", "CUST-1004"))
    );

    private DemoUserDirectory() {
    }

    static DemoUser find(String id) {
        if (id == null) {
            return null;
        }
        return USERS.stream().filter(user -> user.id().equals(id)).findFirst().orElse(null);
    }

    static String publicUsersJson() {
        String users = USERS.stream().map(DemoUser::publicJson).collect(java.util.stream.Collectors.joining(","));
        return "{\"users\":[" + users + "]}";
    }
}
