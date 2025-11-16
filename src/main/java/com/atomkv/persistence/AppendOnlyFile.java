package com.atomkv.persistence;

import com.atomkv.store.InMemoryStore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Very simple AOF-like appender: writes commands as lines to a file.
 * Supports append and replay on startup.
 */
public class AppendOnlyFile implements AutoCloseable {
    private final Path file;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread writerThread;
    private BufferedWriter writer;

    public AppendOnlyFile(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.getParent());
        boolean exists = Files.exists(file);
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        writerThread = new Thread(this::writerLoop, "aof-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void writerLoop() {
        try {
            while (running.get() || !queue.isEmpty()) {
                String cmd = queue.take();
                writer.write(cmd);
                writer.write('\n');
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("AOF writer error: " + e.getMessage());
        }
    }

    public void append(String commandLine) {
        if (!running.get()) {
            return;
        }

        queue.offer(commandLine);
    }

    public void replay(InMemoryStore store) throws IOException {
        if (!Files.exists(file)) {
            return;
        }

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                store.applyCommandFromAOF(line);
            }
        }
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        writerThread.interrupt();
        
        try {
            writerThread.join(1000);
        } catch (InterruptedException ignored) {}
        
        if (writer != null) {
            writer.close();
        }
    }
}
