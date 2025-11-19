package com.atomkv.metrics;

import com.atomkv.store.InMemoryStore;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.util.Map;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP server exposing basic JSON metrics.
 */
public class MetricsServer {
    private final HttpServer server;

    public MetricsServer(int port, InMemoryStore store) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", new MetricsHandler(store));
        server.createContext("/insights", new DataHandler(store));
        server.setExecutor(null);
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    static class MetricsHandler implements HttpHandler {
        private final InMemoryStore store;

        MetricsHandler(InMemoryStore store) {
            this.store = store;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = String.format("{\"keys\":%d,\"hits\":%d,\"misses\":%d}", store.keys(), store.hits(), store.misses());
            
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        }
    }

    static class DataHandler implements HttpHandler {
        private final InMemoryStore store;

        DataHandler(InMemoryStore store) {
            this.store = store;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> snapshot = store.snapshot();

            StringBuilder sb = new StringBuilder();
            sb.append('{');

            boolean first = true;
            for (Map.Entry<String, String> e : snapshot.entrySet()) {
                if (!first) sb.append(',');
                first = false;

                sb.append(escapeJson(e.getKey()));
                sb.append(':');
                sb.append(escapeJson(e.getValue()));
            }

            sb.append('}');

            byte[] out = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        }

        private static String escapeJson(String s) {
            if (s == null) return "null";

            StringBuilder sb = new StringBuilder();
            sb.append('"');

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }

            sb.append('"');
            return sb.toString();
        }
    }
}
