package com.atomkv.metrics;

import com.atomkv.store.InMemoryStore;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

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
}
