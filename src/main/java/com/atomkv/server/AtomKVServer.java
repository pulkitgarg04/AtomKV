package com.atomkv.server;

import com.atomkv.metrics.MetricsServer;
import com.atomkv.persistence.AppendOnlyFile;
import com.atomkv.store.InMemoryStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AtomKVServer {
    public static void main(String[] args) throws Exception {
        int tcpPort = 6379;
        int metricsPort = 8080;
        int maxEntries = 10000;
        Path aofPath = Path.of(System.getProperty("user.home"), ".atomkv", "appendonly.aof");

        System.out.println("Starting AtomKV...");

        AppendOnlyFile aof = new AppendOnlyFile(aofPath);
        InMemoryStore store = new InMemoryStore(maxEntries, aof);

        try {
            aof.replay(store);
        } catch (IOException e) {
            System.err.println("AOF replay failed: " + e.getMessage());
        }

        MetricsServer metrics = new MetricsServer(metricsPort, store);
        metrics.start();

        System.out.println("Metrics available at http://localhost:" + metricsPort + "/metrics");

        ExecutorService clients = Executors.newCachedThreadPool(r -> new Thread(r, "client-worker"));

        try (ServerSocket ss = new ServerSocket(tcpPort)) {
            System.out.println("AtomKV listening on port " + tcpPort);
            
            while (true) {
                Socket s = ss.accept();
                clients.submit(new ClientHandler(s, store));
            }
        } catch (IOException e) {
            System.err.println("Server socket failed: " + e.getMessage());
        } finally {
            clients.shutdownNow();
            metrics.stop(0);
            store.close();
            aof.close();
        }
    }
}
