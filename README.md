# AtomKV

AtomKV is a lightweight in-memory key-value store featuring a simple text protocol over TCP (GET/SET/DEL/TTL/PERSIST), TTL support, LRU-based eviction, append-only file (AOF) persistence, and a minimal HTTP endpoint for metrics.

## Requirements

- JDK 21 (or later): project is compiled with `--release 21`.
- Maven 3.8+ (the project uses standard Maven lifecycle).

## Build & test

Build (skip tests):

```bash
mvn -q -DskipTests package
```

Run tests:
## Run the server

The server stores its append-only file at `~/.atomkv/appendonly.aof` by default.
Defaults:
- TCP: 6379
- Metrics HTTP: 8080

Run the fat-jar produced by the `mvn package` step:

```bash
java -jar target/atomkv-0.1.0.jar
```



```bash
mvn clean test
```

The server stores its append-only file at `~/.atomkv/appendonly.aof` by default.
