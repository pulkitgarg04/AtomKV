package com.atomkv.store;

import com.atomkv.persistence.AppendOnlyFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.List;

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

    @Test
    public void testExistsAndDel() throws Exception {
        store = new InMemoryStore(100, null);

        store.set("a", "1", null);
        assertTrue(store.exists("a"));
        assertTrue(store.del("a"));
        assertFalse(store.exists("a"));
        assertFalse(store.del("a"));
    }

    @Test
    public void testKeysPatternAppendStrlen() throws Exception {
        store = new InMemoryStore(100, null);

        store.set("foo1", "v", null);
        store.set("foo2", "v", null);
        store.set("bar", "v", null);

        List<String> keys = store.keys("foo*");
        assertEquals(2, keys.size());

        store.set("s", "hello", null);
        int newLen = store.append("s", " world");
        assertEquals(11, newLen);
        assertEquals(11, store.strlen("s"));
    }

    @Test
    public void testPersistTtlSnapshotApplyAOFAndTypeHitsMisses() throws Exception {
        store = new InMemoryStore(100, null);

        store.set("t", "v", Duration.ofMillis(500));
        long ttlBefore = store.ttl("t");
        assertTrue(ttlBefore > 0);
        assertEquals("ttl_key", store.type("t"));
        assertTrue(store.persist("t"));
        assertEquals(-1, store.ttl("t"));
        assertEquals("string", store.type("t"));

        Map<String, String> snap = store.snapshot();
        assertTrue(snap.containsKey("t"));

        store.applyCommandFromAOF("SET aoof v1");
        assertTrue(store.exists("aoof"));
        store.applyCommandFromAOF("APPEND aoof v2");
        assertEquals("v1v2", store.get("aoof").orElse(null));
        store.applyCommandFromAOF("DEL aoof");
        assertFalse(store.exists("aoof"));

        store.applyCommandFromAOF("FLUSHALL");
        assertEquals(0, store.keys());

        long missCountBefore = store.misses();
        assertTrue(store.get("no-such-key").isEmpty());
        assertEquals(missCountBefore + 1, store.misses());

        store.set("h", "1", null);
        long hitsBefore = store.hits();
        assertTrue(store.get("h").isPresent());
        assertEquals(hitsBefore + 1, store.hits());
    }
}
