# Contributing to BlinkHouse

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for integration tests)

## Build

```bash
# Full build — unit tests + integration tests + Checkstyle
mvn verify

# Skip integration tests (no Docker needed)
mvn verify -DskipITs

# Skip Checkstyle (during active development)
mvn verify -Dcheckstyle.skip=true
```

## Project structure

| Module | Contents |
|---|---|
| `blinkhouse-core` | Zero-Spring core: type system, RowBinary protocol, `ChTemplate`, `TypeHandler` SPI |
| `blinkhouse-spring` | Spring Data repository infrastructure, `@EnableClickHouseRepositories` |
| `blinkhouse-spring-boot-starter` | Auto-configuration, `clickhouse.*` properties |
| `blinkhouse-processor` | Annotation processor — generates compile-time metamodel |
| `blinkhouse-test` | `@ClickHouseTest` slice, Testcontainers helpers, fixture loading |
| `blinkhouse-benchmark` | JMH suite — NFR-1 and NFR-2 gates |

The most important rule: **`blinkhouse-core` must have zero Spring dependencies.** This is enforced by an ArchUnit test. Do not import anything from `org.springframework` in `blinkhouse-core/src/main`.

## Adding a type handler

1. Create `YourTypeHandler.java` in `blinkhouse-core/src/main/java/io/blinkhouse/core/type/handler/`.
2. Implement `TypeHandler<J>`:
   ```java
   public final class YourTypeHandler implements TypeHandler<YourJavaType> {
       @Override public String clickHouseTypeName() { return "YourClickHouseType"; }
       @Override public void write(ChOutputStream out, YourJavaType v) throws IOException { ... }
       @Override public YourJavaType read(ChInputStream in) throws IOException { ... }
   }
   ```
3. Add a round-trip test in `TypeHandlerRoundTripIT` — one test per handler, following the existing pattern.
4. Consult the ClickHouse RowBinary documentation for the exact wire format of the type.

Common traps:
- **UUID byte order** — ClickHouse uses MSB-first then LSB-first, each written as little-endian `Int64`. Do not use `ByteBuffer.wrap` with RFC 4122 order.
- **Nullable** — the null flag byte is `0x01` for null, `0x00` for non-null (inverted from intuition).
- **DateTime64 timezone** — always read the timezone from the column type; never fall back to `ZoneId.systemDefault()`.
- **LEB128** — string and array lengths use unsigned LEB128, not fixed-width integers.

## Integration tests

Integration tests are named `*IT.java` and run via Maven Failsafe. They require Docker.

All ITs in `blinkhouse-core` use the shared `ClickHouseContainerExtension.INSTANCE` container — do not start a new container per test class. The shared instance keeps the full suite under 5 minutes.

The ClickHouse image can be overridden for local testing:
```bash
BH_CLICKHOUSE_IMAGE=clickhouse/clickhouse-server:25.1 mvn verify
```

## Code style

Checkstyle runs on every build (`mvn verify`). Rules:

- No star imports
- Braces required on all blocks
- Public API classes and methods require Javadoc
- No tab characters

Tests and the benchmark module are exempt from Javadoc requirements.

To check style without a full build:
```bash
mvn checkstyle:check
```

## Submitting a pull request

1. Fork and create a branch from `main`.
2. Make your changes. Add or update tests.
3. Run `mvn verify` locally — CI will refuse a PR that fails.
4. Open a PR against `main` with a clear description of what changed and why.

If your change affects a transport or architecture decision, update or create the relevant ADR in `docs/adr/`.
