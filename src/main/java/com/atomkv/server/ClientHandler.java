package com.atomkv.server;

import com.atomkv.store.InMemoryStore;

import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.util.Locale;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final InMemoryStore store;

    public ClientHandler(Socket socket, InMemoryStore store) {
        this.socket = socket;
        this.store = store;
    }
 
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            String line;

            out.write("OK AtomKV\n");
            out.flush();

            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(" ", 4);
                String cmd = parts[0].toUpperCase(Locale.ROOT);
                
                switch (cmd) {
                    case "GET": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        var val = store.get(parts[1]);
                        out.write(val.map(v -> "+" + v + "\n").orElse("$-1\n"));
                        
                        break;
                    }
                    
                    case "SET": {
                        if (parts.length < 3) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        String key = parts[1];
                        String value = parts[2];
                        Duration ttl = null;

                        if (parts.length >= 4) {
                            String rest = parts[3];
                            String[] toks = rest.split(" ");

                            if (toks.length >= 2 && "PX".equalsIgnoreCase(toks[0])) {
                                try {
                                    ttl = Duration.ofMillis(Long.parseLong(toks[1]));
                                } catch (NumberFormatException ignored) {
                                    // invalid number, ignore :)
                                }
                            }
                        }

                        store.set(key, value, ttl);
                        out.write("+OK\n");
                        break;
                    }

                    case "DEL": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }
                        
                        boolean removed = store.del(parts[1]);
                        out.write((removed ? ":1\n" : ":0\n"));
                        
                        break;
                    }

                    case "TTL": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        long ttl = store.ttl(parts[1]);
                        out.write(":" + ttl + "\n");
                        
                        break;
                    }

                    case "PERSIST": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        boolean ok = store.persist(parts[1]);
                        out.write((ok ? ":1\n" : ":0\n"));
                        
                        break;
                    }

                    case "QUIT": {
                        out.write("+BYE\n");
                        out.flush();
                        return;
                    }

                    default:
                        out.write("-ERR unknown command\n");
                }

                out.flush();
            }
        } catch (IOException e) {
            // client disconnected or IO issue
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
