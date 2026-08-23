# BlinkHouse

**ClickHouse set-up in a blink for your Java application.**

Not an ORM. ClickHouse is a columnar OLAP database — it has no row-level identity, no transactions, no dirty checking, and no foreign keys. BlinkHouse doesn't pretend otherwise. What it gives you instead:

- **Typed entities and repositories** — `@ChTable`, `@ChColumn`, Spring Data-style `findBy` methods, `@Query` for native SQL — without any of the JPA lies.
- **High-throughput batch ingestion** — a `BatchWriter` that serialises directly to ClickHouse's binary wire format (`RowBinary`), with buffering, flush triggers, backpressure, retry, and dead-letter callbacks built in. ~2.9M rows/sec on commodity hardware.
- **A query API that speaks ClickHouse** — `PREWHERE`, `FINAL`, `SAMPLE`, `WITH TOTALS`, `LIMIT n BY`, aggregate combinators (`uniqIf`, `quantileMerge`), explicit join hints. First-class citizens, not escape hatches.
- **Schema as code** — generate `CREATE TABLE` DDL from annotations, detect drift against `system.columns`, emit migration scripts for Flyway/Liquibase. Never auto-applies destructive changes.
- **Spring Boot starter** — zero-config auto-wiring, `clickhouse.*` properties, Actuator health indicator, Micrometer metrics, OpenTelemetry spans.
- **No Spring in the core** — `blinkhouse-core` is plain Java. Use it from Quarkus, Micronaut, or a plain `main()`.

---

## What BlinkHouse is not

| JPA concept | BlinkHouse answer |
|---|---|
| `@Transactional` | ClickHouse has no transactions. Don't fake it. |
| `@OneToMany` / lazy loading | N+1 against a columnar store is catastrophic. Use explicit joins or dictionaries. |
| `EntityManager` / dirty checking | ClickHouse rows are immutable after insert. |
| Row-level `UPDATE` / `DELETE` | Exposed only via an explicit `MutationOperations` API — loudly named on purpose. |
| Offset pagination | Supported but logged as a warning above a configurable threshold. Use keyset/cursor pagination. |

The "Coming from JPA" guide lives in the docs site and is considered a feature, not an apology.

---

## Status

**Phase 0 — in progress.** Transport and type-system spikes complete. Not yet released.

| Spike | Status | Result |
|---|---|---|
| A — Transport bake-off | Done | Raw HTTP + RowBinary: ~2.9M rows/sec. Split transport selected (see `docs/adr/ADR-04-transport.md`). |
| B — Type round-trip | Done | 12/12 hard types correct. JSON skipped (internal binary format — tracked). |
| C — Spring Data SPI | Done | `@EnableClickHouseRepositories` + `RepositoryFactoryBean` SPI wires correctly. Fragment composition dispatched through proxy. No need to implement proxying from scratch (Q-5). |

---

## Quick start (coming in v0.1.0)

```java
@ChTable(name = "page_views", engine = @ChEngine(MergeTree.class),
         orderBy = {"tenant_id", "ts"})
public record PageView(
    @ChColumn("tenant_id") int tenantId,
    @ChColumn("ts")        Instant ts,
    @ChColumn("user_id")   UUID userId,
    @ChColumn("country")   @LowCardinality String country
) {}

public interface PageViewRepository extends ClickHouseRepository<PageView, UUID> {
    Slice<PageView> findByTenantIdAndTsBetween(int tenantId, Instant from, Instant to, Cursor c);

    @Query("SELECT toStartOfHour(ts) h, uniq(user_id) u " +
           "FROM page_views WHERE tenant_id = :t GROUP BY h ORDER BY h")
    List<HourlyUniques> hourlyUniques(@Param("t") int tenantId);
}
```

---

## Modules

| Module | Description |
|---|---|
| `blinkhouse-core` | Type system, RowBinary serialisation, `ChTemplate`, zero Spring. |
| `blinkhouse-spring` | Repository infrastructure, Spring Data integration, exception translation. |
| `blinkhouse-spring-boot-starter` | Auto-configuration, `clickhouse.*` properties, Actuator, Micrometer. |
| `blinkhouse-processor` | Annotation processor — generates compile-time metamodel (`PageView_`). Optional. |
| `blinkhouse-test` | `@ClickHouseTest` slice, Testcontainers auto-start, fixture loading, table truncation. |
| `blinkhouse-benchmark` | JMH suite. Gates NFR-1 (write throughput) and NFR-2 (read mapping overhead) in CI. |

---

## Design principles (binding, not aspirational)

1. **No lies about semantics.** If ClickHouse can't do it, BlinkHouse won't fake it.
2. **Batch is the default write path.** Single-row `insert()` is instrumented and logged as a smell.
3. **Analytics is a first-class query shape.** Aggregations and ClickHouse-specific clauses are in the typed API.
4. **The escape hatch is always one call away.** Drop to native SQL at any point, keeping the same row mapping and metrics.
5. **Schema is code, migrations are explicit.** DDL is generated and diffed — never silently applied in production.
6. **No relational modelling.** Joins are explicit. There are no cascades, no proxies, no `@OneToMany`.

---

## Build

```bash
./mvnw verify          # full build + integration tests (requires Docker)
./mvnw verify -DskipITs  # skip integration tests
```

Java 21 · Maven 3.9+ · Docker (for integration tests)

---

## License

Apache 2.0
