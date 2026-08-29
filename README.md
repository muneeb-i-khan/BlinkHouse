<div align="center">
  <img src="assets/logo3.png" alt="BlinkHouse" width="260" />
  <br/>
  <strong>ClickHouse persistence for Java — done right.</strong>
  <br/><br/>

  ![Build](https://img.shields.io/github/actions/workflow/status/muneeb-i-khan/BlinkHouse/ci.yml?branch=main&label=build&style=flat-square)
  ![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)
  ![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square)
  ![Maven Central](https://img.shields.io/maven-central/v/io.github.muneeb-i-khan/blinkhouse-spring-boot-starter?style=flat-square&label=maven-central)
</div>

---

BlinkHouse is a ClickHouse-native persistence library for Java and Spring Boot. High-throughput buffered ingest, typed queries, schema management, and Spring Data repositories — without the lies an ORM would tell you.

**It is not an ORM.** ClickHouse has no transactions, no row-level identity, no dirty checking, and no foreign keys. BlinkHouse doesn't fake any of that.

---

## Install

```xml
<dependency>
    <groupId>io.github.muneeb-i-khan</groupId>
    <artifactId>blinkhouse-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Not on Spring Boot? Use `blinkhouse-core` directly — zero Spring dependencies.

---

## Quick start

**1 — Configure**

```yaml
clickhouse:
  url: http://localhost:8123
  username: default
  password: ${CH_PASSWORD}
  database: analytics
  schema:
    mode: CREATE_IF_MISSING
```

**2 — Define an entity**

```java
@ChTable(
    name = "page_views",
    orderBy = {"tenant_id", "ts"},
    partitionBy = "toYYYYMM(ts)"
)
@ChEngine(value = Engine.REPLACING_MERGE_TREE, versionColumn = "ingested_at")
public record PageView(
    @ChColumn(type = "UInt32")                int     tenantId,
    @ChColumn(type = "DateTime64(3,'UTC')")   Instant ts,
    @ChColumn(type = "LowCardinality(String)") String  country,
                                              String  url,
    @ChColumn(defaultExpression = "now64(3)") Instant ingestedAt
) {}
```

**3 — Ingest at scale**

```java
@Service
public class IngestService {

    private final ChTemplate template;

    public void ingest(List<PageView> views) throws InterruptedException {
        try (BatchWriter<PageView> writer = template.batchWriter(PageView.class, BatchWriterConfig.defaults())) {
            for (PageView v : views) writer.add(v);
        }
    }
}
```

**4 — Query**

```java
// Spring Data repository
public interface PageViewRepository extends ClickHouseRepository<PageView, UUID> {
    Slice<PageView> findByTenantIdAndTsBetween(int tenantId, Instant from, Instant to, Cursor cursor);

    @Query("SELECT toStartOfHour(ts) h, uniq(url) u FROM page_views WHERE tenant_id = :t GROUP BY h ORDER BY h")
    List<HourlyUniques> hourlyUniques(@Param("t") int tenantId);
}

// Or use the DSL directly
List<PageView> views = ChQuery.select("*")
    .from(TableRef.of("page_views"))
    .where(col("tenant_id").eq(tenantId))
    .fetch(template, PageView.class);
```

---

## Key imports

```java
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.write.BatchWriter;
import io.blinkhouse.core.write.BatchWriterConfig;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChEngine;
import io.blinkhouse.core.query.ChQuery;
import io.blinkhouse.spring.repository.ClickHouseRepository;
```

The Maven `groupId` is `io.github.muneeb-i-khan`. Java package names inside the JAR are `io.blinkhouse.*`.

---

## What you get

| Capability | Detail |
|---|---|
| **BatchWriter\<T\>** | Ring-buffer ingest with three flush triggers: row count, byte size, elapsed time. Exponential backoff retry. Dead-letter dispatch. No silent drops. |
| **Full type system** | All ClickHouse types covered — geo, AggregateFunction, LowCardinality, Nullable, Array, Map, Tuple. All round-trip tested. |
| **ChQuery DSL** | FINAL, SAMPLE, PREWHERE, LIMIT n BY, WITH TOTALS, window functions, -Merge combinators, dictGet, geoDistance. ClickHouse-first. |
| **Spring Data repositories** | Derived query methods, `@Query` native SQL, keyset pagination as primary. Offset pagination works but warns above your threshold. |
| **Schema management** | NONE / VALIDATE / CREATE_IF_MISSING / UPDATE modes. Destructive changes require two opt-ins. EngineMismatch and OrderByMismatch are never auto-fixed. |
| **Observability** | Micrometer metrics, OTel tracing, correlatable `query_id` joinable against `system.query_log`. Grafana dashboard JSON included. |
| **Connection pooling** | Apache HttpClient 5 pool shared across `ChTemplate` and all its `BatchWriter` children. Idle evictor, inactivity validation, full YAML tuning. |
| **Advanced features** | `@ChMaterializedView`, `@ChDictionary`, `MutationOperations` (ALTER DELETE/UPDATE), `optimize()` for forced merges, GraalVM native-image hints. |
| **Spring-free core** | `blinkhouse-core` has zero Spring imports. Works from Quarkus, Micronaut, or a plain `main()`. |

---

## What it doesn't do

These are design decisions, not gaps.

| Concept | BlinkHouse answer |
|---|---|
| `@Transactional` | ClickHouse has no transactions. BlinkHouse won't fake it. |
| `@OneToMany` / lazy loading | No relations. Use joins or dictionaries (`dictGet`). |
| Dirty checking / identity map | ClickHouse rows are immutable after insert. |
| Silent row-level `UPDATE`/`DELETE` | Exposed only via explicit `MutationOperations` — loudly named on purpose. |
| Deep offset pagination | Supported but logged as a warning. Use keyset (`Cursor`) instead. |

---

## Modules

| Artifact | Use when |
|---|---|
| `blinkhouse-spring-boot-starter` | Spring Boot app — pulls in everything |
| `blinkhouse-core` | Plain Java / Quarkus / Micronaut — zero Spring |
| `blinkhouse-spring` | Spring without Boot autoconfiguration |
| `blinkhouse-test` | `@BlinkHouseTest` slice + Testcontainers fixtures |
| `blinkhouse-processor` | Optional annotation processor for typed metamodel |

---

## Compatibility

| | Supported |
|---|---|
| Java | 17, 21 |
| Spring Boot | 3.2, 3.3, 3.4 |
| ClickHouse server | 24.3, 24.8, 25.1+ |
| Build tool | Maven 3.9+, Gradle (via Maven Central) |
| GraalVM native | reflect-config + resource-config included |

---

## Building

```bash
# Full build + integration tests (requires Docker)
mvn verify

# Skip integration tests
mvn verify -DskipITs

# Release build (requires GPG key + Sonatype credentials)
mvn clean deploy -P release -DskipTests
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the contributor guide and PR checklist.

---

## Coming from JPA?

Read [docs/05-COMING-FROM-JPA.md](docs/05-COMING-FROM-JPA.md) for a mapping of JPA concepts to their BlinkHouse equivalents (and the ones that simply don't exist).

---

## License

[Apache License 2.0](LICENSE)
