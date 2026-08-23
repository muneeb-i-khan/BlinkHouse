<div align="center">
  <img src="assets/logo3.png" alt="BlinkHouse" width="260" />
  <br/>
  <strong>ClickHouse persistence for Java — done right.</strong>
  <br/><br/>

  ![Build](https://img.shields.io/github/actions/workflow/status/muneebillahi/blinkhouse/ci.yml?branch=main&label=build&style=flat-square)
  ![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)
  ![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square)
  ![Phase](https://img.shields.io/badge/phase-0%20complete-brightgreen?style=flat-square)
  ![Maven Central](https://img.shields.io/badge/maven--central-not%20yet%20released-lightgrey?style=flat-square)
</div>

---

BlinkHouse is a ClickHouse-native persistence framework for Java and Spring Boot. It gives you typed entities, high-throughput batch ingestion via the RowBinary wire format, a query API that speaks ClickHouse fluently, and a Spring Data repository layer — without pretending ClickHouse is a relational database.

**It is not an ORM.** ClickHouse has no transactions, no row-level identity, no dirty checking, and no foreign keys. BlinkHouse doesn't fake any of that.

---

## What you get

| Capability | Detail |
|---|---|
| **Typed entities** | `@ChTable`, `@ChColumn`, `@ChEngine` — annotate a Java record, get a mapped entity. |
| **Spring Data repositories** | `ClickHouseRepository<T, ID>` with `findBy` methods, `@Query` for native SQL, and fragment composition (`XxxRepositoryImpl`). |
| **High-throughput batch writes** | `BatchWriter` serialises directly to RowBinary. ~2.9M rows/sec on commodity hardware. Buffering, flush triggers, backpressure, retry, and dead-letter callbacks included. |
| **ClickHouse-native query API** | `PREWHERE`, `FINAL`, `SAMPLE`, `WITH TOTALS`, `LIMIT n BY`, aggregate combinators. First-class citizens, not escape hatches. |
| **Schema as code** | Generate `CREATE TABLE` DDL from annotations, detect drift against `system.columns`, emit migration scripts for Flyway or Liquibase. Never auto-applies destructive changes. |
| **Spring Boot starter** | Zero-config auto-wiring, `clickhouse.*` properties, Actuator health indicator, Micrometer metrics, OpenTelemetry spans. |
| **Core is Spring-free** | `blinkhouse-core` is plain Java. Works from Quarkus, Micronaut, or a plain `main()`. |

---

## Quick start <sup>(available in v0.1.0)</sup>

```java
@ChTable(name = "page_views", engine = @ChEngine(MergeTree.class),
         orderBy = {"tenant_id", "ts"})
public record PageView(
    @ChColumn("tenant_id") int    tenantId,
    @ChColumn("ts")        Instant ts,
    @ChColumn("user_id")   UUID    userId,
    @ChColumn("country")   @LowCardinality String country
) {}
```

```java
public interface PageViewRepository extends ClickHouseRepository<PageView, UUID> {

    Slice<PageView> findByTenantIdAndTsBetween(
            int tenantId, Instant from, Instant to, Cursor c);

    @Query("""
           SELECT toStartOfHour(ts) h, uniq(user_id) u
           FROM page_views
           WHERE tenant_id = :t
           GROUP BY h ORDER BY h
           """)
    List<HourlyUniques> hourlyUniques(@Param("t") int tenantId);
}
```

```java
// Spring Boot — zero extra config needed
@SpringBootApplication
@EnableClickHouseRepositories
public class App { ... }
```

---

## What BlinkHouse is not

Coming from JPA? This table saves you some surprises.

| JPA concept | BlinkHouse answer |
|---|---|
| `@Transactional` | ClickHouse has no transactions. BlinkHouse won't fake it. |
| `@OneToMany` / lazy loading | N+1 against a columnar store is catastrophic. Use joins or dictionaries. |
| `EntityManager` / dirty checking | ClickHouse rows are immutable after insert. |
| Row-level `UPDATE` / `DELETE` | Exposed only via an explicit `MutationOperations` API — loudly named on purpose. |
| Offset pagination | Supported but logged as a warning above a configurable threshold. Use keyset/cursor pagination instead. |

---

## Project status

**Phase 0 is complete.** Transport decision made, type system verified, Spring Data SPI wired. Core implementation begins in Phase 1.

| Milestone | Status | Summary |
|---|---|---|
| Transport bake-off | ✅ Done | Raw HTTP + RowBinary selected. ~2.9M rows/sec. See [ADR-04](docs/adr/ADR-04-transport.md). |
| Type round-trip (12 types) | ✅ Done | All hard types correct. JSON skipped (internal RowBinary format — tracked). |
| Spring Data SPI wiring | ✅ Done | `@EnableClickHouseRepositories` + `RepositoryFactoryBean` wire correctly. Fragment composition dispatched through proxy. |
| CI pipeline | ✅ Done | GitHub Actions: build, integration tests (ClickHouse 24.3 / 24.8 / 25.1 matrix), benchmark compile gate. |
| Core mapping & read path | 🔜 Phase 1 | `@ChTable`, `ChTemplate`, `RowMapper`, streaming queries. Target: v0.1.0. |
| Batch write path | 🔜 Phase 2 | `BatchWriter`, RowBinary serialisation, NFR-1 gate in CI. |
| Schema management | 🔜 Phase 3 | DDL generation, drift detection, migration script output. |
| Spring Boot starter | 🔜 Phase 4 | Auto-configuration, Actuator, Micrometer, OpenTelemetry. |

---

## Modules

```
blinkhouse/
├── blinkhouse-core/              # Type system, RowBinary I/O, ChTemplate. No Spring.
├── blinkhouse-spring/            # Repository SPI, Spring Data integration.
├── blinkhouse-spring-boot-starter/ # Auto-configuration, clickhouse.* properties.
├── blinkhouse-processor/         # Annotation processor — compile-time metamodel.
├── blinkhouse-test/              # @ClickHouseTest slice, Testcontainers helpers.
├── blinkhouse-benchmark/         # JMH suite — NFR-1 write throughput, NFR-2 read overhead.
└── examples/                     # Runnable examples.
```

---

## Design principles

These are binding constraints, not aspirations.

1. **No lies about semantics.** If ClickHouse can't do it, BlinkHouse won't fake it.
2. **Batch is the default write path.** Single-row `insert()` is instrumented and logged as a smell.
3. **Analytics is a first-class query shape.** Aggregations and ClickHouse-specific clauses live in the typed API, not in string escape hatches.
4. **The escape hatch is always one call away.** Drop to native SQL at any point while keeping the same row mapping and metrics.
5. **Schema is code, migrations are explicit.** DDL is generated and diffed — never silently applied in production.
6. **No relational modelling.** Joins are explicit. There are no cascades, no proxies, no `@OneToMany`.

---

## Building

```bash
# Full build + integration tests (requires Docker)
./mvnw verify

# Skip integration tests
./mvnw verify -DskipITs

# Checkstyle only
./mvnw checkstyle:check
```

**Requirements:** Java 17+ · Maven 3.9+ · Docker (for integration tests)

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full contributor guide, common traps, and PR checklist.

---

## License

[Apache License 2.0](LICENSE)
