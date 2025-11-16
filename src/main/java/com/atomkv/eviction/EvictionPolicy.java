package com.atomkv.eviction;

import java.util.Optional;

public interface EvictionPolicy {
    void recordAccess(String key);

    void recordPut(String key);
    
    void recordRemove(String key);
    
    Optional<String> evictKeyIfNeeded(int currentSize);
    
    int capacity();
}