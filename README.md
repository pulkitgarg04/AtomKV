# AtomKV

![AtomKV](https://socialify.git.ci/pulkitgarg04/AtomKV/image?font=JetBrains+Mono&language=1&name=1&owner=1&pattern=Charlie+Brown&theme=Dark)

**AtomKV** is a lightweight in-memory key-value store featuring a simple text protocol over TCP (GET/SET/DEL/TTL/PERSIST), TTL support, LRU-based eviction, append-only file (AOF) persistence, and a minimal HTTP endpoint for metrics.

## Requirements

- JDK 21 (or later): project is compiled with `--release 21`.
- Maven 3.8+ (the project uses standard Maven lifecycle).

## Build & test
- Build (skip tests):
    ```bash
    mvn -q -DskipTests package
    ```

- Run tests:
    ```bash
    mvn clean test
    ```

## Run the server

The server stores its append-only file at `~/.atomkv/appendonly.aof` by default.

**Defaults**:
- TCP: 6379
- Metrics HTTP: 8080

Run the jar produced by the `mvn package` step (artifactId/version come from `pom.xml`):

```bash
java -jar target/atomkv-1.0.jar
```

```bash
chmod +x scripts/atomkv.sh
./scripts/atomkv.sh
```

## Client example (using netcat)

You can connect to the TCP port with `nc` and use the simple text protocol. Example session:

```bash
# connect
nc localhost 6379
# After connect you should see:
OK AtomKV

SET mykey hello
# +OK
GET mykey
# +hello
DEL mykey
# :1
QUIT
# +BYE
```
