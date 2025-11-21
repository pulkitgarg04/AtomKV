package com.atomkv.store;

import com.atomkv.eviction.EvictionPolicy;
import com.atomkv.eviction.LRUEvictionPolicy;
import com.atomkv.persistence.AppendOnlyFile;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory store with TTL, eviction, and AOF persistence hooks.
 */
public class InMemoryStore {
    private final ConcurrentHashMap<String, ValueWrapper> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService janitor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ttl-janitor"));
    private final EvictionPolicy evictionPolicy;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AppendOnlyFile aof;

    public InMemoryStore(int maxEntries, AppendOnlyFile aof) {
        this.evictionPolicy = new LRUEvictionPolicy(maxEntries);
        this.aof = aof;
        janitor.scheduleAtFixedRate(this::cleanupExpired, 1, 1, TimeUnit.SECONDS); // run cleanup every second
    }

    public Optional<String> get(String key) {
        ValueWrapper vw = map.get(key);

        if (vw == null) {
            misses.incrementAndGet();

            return Optional.empty();
        }

        if (vw.isExpired()) {
            map.remove(key);
            evictionPolicy.recordRemove(key);
            misses.incrementAndGet();

            return Optional.empty();
        }

        evictionPolicy.recordAccess(key);
        hits.incrementAndGet();

        return Optional.of(vw.getValue());
    }

    public void set(String key, String value, Duration ttl) {
        long expireAt = (ttl == null) ? -1 : (System.currentTimeMillis() + ttl.toMillis());

        map.put(key, new ValueWrapper(value, expireAt));
        evictionPolicy.recordPut(key);

        if (aof != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("SET ").append(escape(key)).append(' ').append(escape(value));

            if (ttl != null) {
                sb.append(" PX ").append(ttl.toMillis());
            }
            sb.append(" ");

            aof.append(sb.toString());
        }

        evictIfNeeded();
    }

    public boolean del(String key) {
        ValueWrapper removed = map.remove(key);

        if (removed != null) {
            evictionPolicy.recordRemove(key);

            if (aof != null) {
                aof.append("DEL " + escape(key));
            }
            
            return true;
        }

        return false;
    }

    public long ttl(String key) {
        ValueWrapper vw = map.get(key);

        if (vw == null) {
            return -2;
        }

        long ttl = vw.ttlMillis();
        return ttl;
    }

    public boolean persist(String key) {
        ValueWrapper vw = map.get(key);

        if (vw == null) {
            return false;
        }

        vw.setExpireAtMillis(-1);
        
        if (aof != null) {
            aof.append("PERSIST " + escape(key));
        }

        return true;
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ValueWrapper> entry : map.entrySet()) {
            ValueWrapper v = entry.getValue();
            if (v.getExpireAtMillis() > 0 && v.getExpireAtMillis() <= now) {
                map.remove(entry.getKey(), v);
                evictionPolicy.recordRemove(entry.getKey());
            }
        }
    }

    private void evictIfNeeded() {
        Optional<String> toEvict = evictionPolicy.evictKeyIfNeeded(map.size());

        toEvict.ifPresent(k -> {
            map.remove(k);
            if (aof != null) {
                aof.append("DEL " + escape(k));
            }
        });
    }

    public long keys() {
        return map.size();
    }

    public boolean exists(String key) {
        ValueWrapper vw = map.get(key);

        if (vw == null) {
            return false;
        }

        if (vw.isExpired()) {
            map.remove(key);
            evictionPolicy.recordRemove(key);
            return false;
        }

        return true;
    }

    public List<String> keys(String pattern) {
        if (pattern == null) pattern = "*";
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        regex.append('^');
        for (int i = 0; i < parts.length; i++) {
            regex.append(Pattern.quote(parts[i]));
            if (i < parts.length - 1) {
                regex.append(".*");
            }
        }
        regex.append('$');
        Pattern p = Pattern.compile(regex.toString());
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ValueWrapper> entry : map.entrySet()) {
            String k = entry.getKey();
            ValueWrapper v = entry.getValue();
            if (v == null) continue;
            long exp = v.getExpireAtMillis();
            if (exp > 0 && exp <= now) continue;
            if (p.matcher(k).matches()) {
                out.add(k);
            }
        }

        return out;
    }

    public String type(String key) {
        ValueWrapper vw = map.get(key);

        if (vw == null) {
            return "none";
        }

        if (vw.isExpired()) {
            map.remove(key);
            evictionPolicy.recordRemove(key);
            return "none";
        }

        if (vw.getExpireAtMillis() > 0) {
            return "ttl_key";
        }

        String v = vw.getValue();
        if (v == null) return "string";

        try {
            Long.parseLong(v);
            return "number";
        } catch (NumberFormatException ignored) {}

        try {
            Double.parseDouble(v);
            return "number";
        } catch (NumberFormatException ignored) {}

        return "string";
    }

    public void flushAll() {
        map.clear();

        if (aof != null) {
            aof.append("FLUSHALL");
        }
    }

    public int append(String key, String suffix) {
        ValueWrapper vw = map.get(key);

        if (vw == null || vw.isExpired()) {
            set(key, suffix, null);
            return suffix.length();
        }

        vw.appendValue(suffix);

        if (aof != null) {
            aof.append("APPEND " + escape(key) + " " + escape(suffix));
        }

        return vw.getValue() == null ? 0 : vw.getValue().length();
    }

    public long strlen(String key) {
        ValueWrapper vw = map.get(key);

        if (vw == null || vw.isExpired()) {
            return 0;
        }

        String v = vw.getValue();
        return v == null ? 0 : v.length();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public void close() throws Exception {
        janitor.shutdownNow();
        if (aof != null) {
            aof.close();
        }
    }

    /**
     * Return a shallow copy snapshot of current key->value pairs.
     * Expired entries are skipped.
     */
    public Map<String, String> snapshot() {
        Map<String, String> copy = new java.util.HashMap<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ValueWrapper> entry : map.entrySet()) {
            ValueWrapper v = entry.getValue();
            if (v == null) continue;
            long exp = v.getExpireAtMillis();
            if (exp > 0 && exp <= now) {
                continue;
            }

            copy.put(entry.getKey(), v.getValue());
        }

        return copy;
    }

    /**
     * Return a snapshot containing value and metadata (ttl in ms and expireAt epoch ms) for each key.
     * Expired entries are skipped.
     */
    public Map<String, Map<String, Object>> snapshotWithMetadata() {
        Map<String, Map<String, Object>> out = new java.util.HashMap<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ValueWrapper> entry : map.entrySet()) {
            ValueWrapper v = entry.getValue();
            if (v == null) continue;
            long exp = v.getExpireAtMillis();
            if (exp > 0 && exp <= now) {
                continue;
            }

            Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("value", v.getValue());
            long ttl = (exp <= 0) ? -1L : Math.max(0L, exp - now);
            meta.put("ttl", ttl);
            meta.put("expireAt", exp);

            out.put(entry.getKey(), meta);
        }

        return out;
    }

    public void applyCommandFromAOF(String line) {
        if (line == null || line.isBlank()) {
            return;
        }

        String[] parts = splitPreserveQuotes(line);
        if (parts.length == 0) {
            return;
        }

        String cmd = parts[0].toUpperCase();
        
        try {
            switch (cmd) {
                case "SET": {
                    if (parts.length < 3) {
                        break;
                    }

                    String key = unescape(parts[1]);
                    String value = unescape(parts[2]);
                    
                    long px = -1;
                    if (parts.length >= 5 && "PX".equalsIgnoreCase(parts[3])) {
                        px = Long.parseLong(parts[4]);
                    }
                    
                    set(key, value, (px > 0) ? Duration.ofMillis(px) : null);
                    
                    break;
                }

                case "DEL": {
                    if (parts.length >= 2) {
                        del(unescape(parts[1]));
                    }

                    break;
                }

                case "PERSIST": {
                    if (parts.length >= 2) {
                        persist(unescape(parts[1]));
                    }

                    break;
                }

                case "APPEND": {
                    if (parts.length >= 3) {
                        String key = unescape(parts[1]);
                        String value = unescape(parts[2]);
                        append(key, value);
                    }

                    break;
                }

                case "FLUSHALL": {
                    flushAll();
                    break;
                }

                default:
                    // ignore unknown commands
            }
        } catch (Exception e) {
            System.err.println("Error replaying AOF line: " + line + " -> " + e.getMessage());
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        
        s = s.trim();

        if (s.contains(" ") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\\\"") + '"';
        }

        return s;
    }

    private static String unescape(String s) {
        if (s == null) {
            return null;
        }

        s = s.trim();
        
        if (s.startsWith("\"") && s.endsWith("\"")) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\\\"", "\"");
        }
        
        return s;
    }

    private static String[] splitPreserveQuotes(String line) {
        java.util.List<String> parts = new java.util.ArrayList<>();

        if (line == null || line.isBlank()) {
            return new String[0];
        }

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
        
            if (c == '"') {
                inQuotes = !inQuotes;
                cur.append(c);
                continue;
            }

            if (c == ' ' && !inQuotes) {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }

                continue;
            }

            cur.append(c);
        }

        if (cur.length() > 0) {
            parts.add(cur.toString());
        }

        return parts.toArray(new String[0]);
    }
}