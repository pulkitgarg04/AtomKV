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

                String[] parts = line.split(" ");
                String cmd = parts[0].toUpperCase(Locale.ROOT);
                
                switch (cmd) {
                    case "EXISTS": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        boolean e = store.exists(parts[1]);
                        out.write((e ? ":1\n" : ":0\n"));
                        break;
                    }

                    case "KEYS": {
                        String pattern = "*";
                        if (parts.length >= 2) pattern = parts[1];

                        var keys = store.keys(pattern);
                        if (keys.isEmpty()) {
                            out.write("$-1\n");
                        } else {
                            for (String k : keys) {
                                out.write("+" + k + "\n");
                            }
                        }

                        break;
                    }

                    case "TYPE": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        String t = store.type(parts[1]);
                        out.write("+" + t + "\n");
                        break;
                    }

                    case "FLUSHALL": {
                        store.flushAll();
                        out.write("+OK\n");
                        break;
                    }

                    case "APPEND": {
                        if (parts.length < 3) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        String key = parts[1];
                        String value = parts[2];
                        store.append(key, value);
                        out.write("+OK\n");
                        break;
                    }

                    case "INCR": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        long v = store.incr(parts[1]);
                        out.write(":" + v + "\n");
                        break;
                    }

                    case "DECR": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        long v = store.decr(parts[1]);
                        out.write(":" + v + "\n");
                        break;
                    }

                    case "STRLEN": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        long len = store.strlen(parts[1]);
                        out.write(":" + len + "\n");
                        break;
                    }
                    case "GET": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        var val = store.get(parts[1]);
                        out.write(val.map(v -> "+" + v + "\n").orElse("$-1\n"));
                        
                        break;
                    }
                    
                    case "MGET": {
                        if (parts.length < 2) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        String[] keys = new String[parts.length - 1];
                        System.arraycopy(parts, 1, keys, 0, keys.length);
                        var vals = store.mget(keys);
                        for (String vv : vals) {
                            if (vv == null) out.write("$-1\n");
                            else out.write("+" + vv + "\n");
                        }

                        break;
                    }

                    case "MSET": {
                        if (parts.length < 3 || (parts.length - 1) % 2 != 0) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        String[] kv = new String[parts.length - 1];
                        System.arraycopy(parts, 1, kv, 0, kv.length);
                        store.mset(kv);
                        out.write("+OK\n");
                        break;
                    }

                    case "EXPIRE": {
                        if (parts.length < 3) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        try {
                            long secs = Long.parseLong(parts[2]);
                            int ok = store.expire(parts[1], secs);
                            out.write(":" + ok + "\n");
                        } catch (NumberFormatException e) {
                            out.write("-ERR invalid number\n");
                        }

                        break;
                    }

                    case "RENAME": {
                        if (parts.length < 3) {
                            out.write("-ERR wrong number of args\n");
                            break;
                        }

                        boolean ok = store.rename(parts[1], parts[2]);
                        out.write((ok ? "+OK\n" : "-ERR no such key\n"));
                        break;
                    }

                    case "PING": {
                        out.write("+PONG\n");
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
