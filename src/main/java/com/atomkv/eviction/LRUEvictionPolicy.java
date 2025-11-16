package com.atomkv.eviction;

import java.util.*;

/**
 * Simple synchronized LRU eviction policy using LinkedHashMap.
 * Tracks access order externally and provides a key to evict when size exceeds capacity.
 */
public class LRUEvictionPolicy implements EvictionPolicy {
    private final int capacity;
    private final LinkedHashMap<String, Boolean> accessOrder;

    public LRUEvictionPolicy(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }

    @Override
    public synchronized void recordAccess(String key) {
        accessOrder.put(key, Boolean.TRUE);
    }

    @Override
    public synchronized void recordPut(String key) {
        accessOrder.put(key, Boolean.TRUE);
    }

    @Override
    public synchronized void recordRemove(String key) {
        accessOrder.remove(key);
    }

    @Override
    public synchronized Optional<String> evictKeyIfNeeded(int currentSize) {
        if (currentSize <= capacity) {
            return Optional.empty();
        }

        Iterator<String> it = accessOrder.keySet().iterator();
        
        if (it.hasNext()) {
            String oldest = it.next();
            it.remove();
            return Optional.of(oldest);
        }
        
        return Optional.empty();
    }

    @Override
    public synchronized int capacity() {
        return capacity;
    }
}
