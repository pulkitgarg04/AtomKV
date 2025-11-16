package com.atomkv.store;

import com.atomkv.persistence.AppendOnlyFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryStoreTest {
    private AppendOnlyFile aof;
    private InMemoryStore store;

    @AfterEach
    public void tearDown() throws Exception {
        if (store != null) store.close();
        if (aof != null) aof.close();
    }

    @Test
    public void testSetGetTtl() throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "atomkv-test-aof.aof");
        aof = new AppendOnlyFile(tmp);
        store = new InMemoryStore(100, aof);
        store.set("k1", "v1", null);
        assertTrue(store.get("k1").isPresent());
        store.set("k2", "v2", Duration.ofMillis(200));
        assertTrue(store.get("k2").isPresent());
        Thread.sleep(300);
        assertTrue(store.get("k2").isEmpty());
    }
}
