# ClickORM — Requirements Specification

**Project:** ClickORM — a ClickHouse-native persistence framework for Java & Spring Boot
**Document version:** 1.0
**Status:** Draft for review
**Owner:** _TBD_

---

## 1. Purpose

ClickHouse has no credible, actively-maintained ORM for the JVM. Teams building
analytical services in Spring Boot today fall into one of three unhappy paths:

1. **Bend Spring Data JPA onto ClickHouse.** Requires `ddl-auto=none`, a fake dialect
   (people commonly borrow `MySQLDialect` or `H2Dialect`), native queries for anything
   analytical, and abandonment of JPA for the write path anyway. Hibernate's pagination
   emits `ROW_NUMBER() OVER`, which ClickHouse rejects.
2. **Raw `JdbcTemplate`.** Correct and fast, but every team re-implements row mapping,
   batch buffering, DDL generation, and type handling from scratch.
3. **Abandoned community projects.** Small single-author repos with no releases, no
   ClickHouse version matrix, and no production users.

ClickORM's purpose is to provide the ergonomics teams want from an ORM — declarative
schema, typed queries, repository interfaces, Spring Boot auto-configuration — **without
pretending ClickHouse is a row-oriented OLTP database.**

## 2. Design Philosophy (binding constraints, not aspirations)

These principles constrain every requirement below. A feature that violates one of these
is rejected regardless of user demand.

| # | Principle | Consequence |
|---|-----------|-------------|
| P1 | **No lies about semantics.** | No `@Transactional` illusion, no dirty-checking, no rollback. If ClickHouse can't do it, ClickORM won't fake it. |
| P2 | **Batch is the default write path.** | Single-row `insert()` exists but is documented as a smell and is instrumented so it shows up in metrics. |
| P3 | **Analytics is a first-class query shape.** | Aggregations, window functions, `arrayJoin`, `FINAL`, `SAMPLE`, `GROUP BY WITH ROLLUP` are supported in the typed API — not escape-hatched into raw strings. |
| P4 | **The escape hatch is always one call away.** | Any typed API can be dropped for native SQL with the same row-mapping and metrics machinery. |
| P5 | **Schema is code, migrations are explicit.** | DDL can be *generated* and *diffed*, but never auto-applied in production without an explicit opt-in. |
| P6 | **No relational modelling.** | No `@OneToMany`, no cascades, no lazy proxies, no FK constraints, no N+1. Joins are explicit and typed but never implicit. |

## 3. Stakeholders & Personas

| Persona | Need | Primary success signal |
|---------|------|------------------------|
| **Ingest engineer** | Push 100k+ events/sec into ClickHouse from a Spring service without hand-writing batch buffers. | Throughput within 10% of hand-tuned JDBC batching. |
| **Analytics API developer** | Serve dashboards from typed queries with compile-time column safety. | Renames a column, build fails at compile time. |
| **Platform/SRE** | Observability, connection pooling, health checks, graceful degradation. | Actuator health + Micrometer metrics work out of the box. |
| **Data engineer** | Version-controlled schema, safe migrations, MergeTree engine control. | Full control over `ORDER BY` / `PARTITION BY` / TTL / codecs from annotations. |
| **OSS contributor** | Clear module boundaries, testable core, no Spring dependency in core. | Can contribute a type handler without touching Spring code. |

## 4. Scope

### 4.1 In Scope

- Annotation-driven entity → ClickHouse table mapping
- Full ClickHouse type system coverage (see FR-2)
- MergeTree-family engine configuration (incl. Replicated & Distributed)
- Type-safe query builder with compile-time metamodel generation
- Spring Data-style repository interfaces with derived query methods
- High-throughput batch ingestion with buffering, async-insert support, retry
- DDL generation + schema drift detection + optional migration application
- Spring Boot starter with auto-configuration, actuator, and Micrometer integration
- Testcontainers-based test support module
- Native SQL escape hatch with identical mapping & instrumentation

### 4.2 Explicitly Out of Scope (v1.0)

| Excluded | Rationale |
|----------|-----------|
| ACID transactions / rollback | ClickHouse does not provide them. Faking it is P1 violation. |
| Dirty checking / unit of work / `EntityManager` | Requires row-level identity and update semantics ClickHouse lacks. |
| Lazy loading & entity proxies | Encourages N+1 against a columnar store — catastrophic. |
| `@OneToMany` / `@ManyToMany` / cascades | P6. Use explicit joins or dictionaries. |
| JPA / Jakarta Persistence spec compliance | The spec's contract is unsatisfiable on ClickHouse. |
| Row-level `UPDATE` / `DELETE` convenience methods | Mutations are async background ops. Exposed only via an explicit, loudly-named `MutationOperations` API. |
| Reactive (`Publisher`-based) repositories | Deferred to post-1.0. Non-trivial with the JDBC path. |
| Multi-database portability | ClickHouse-only by design. |
| Kafka/CDC ingestion pipelines | Out of band; ClickHouse has native table engines for this. |

## 5. Functional Requirements

Priority: **M** = Must (v1.0 blocker), **S** = Should, **C** = Could (post-1.0).

### FR-1 — Entity Mapping

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-1.1 | Map a POJO or Java `record` to a ClickHouse table via `@ChTable`. | M |
| FR-1.2 | Map fields to columns via `@ChColumn`, with implicit `snake_case` naming strategy (pluggable). | M |
| FR-1.3 | Support explicit ClickHouse type override per column (e.g. force `LowCardinality(String)`). | M |
| FR-1.4 | Support `MATERIALIZED`, `ALIAS`, `DEFAULT`, `EPHEMERAL` column expressions. | M |
| FR-1.5 | Support per-column compression codecs (`ZSTD(3)`, `Delta`, `DoubleDelta`, `Gorilla`, `T64`, `LZ4HC`). | M |
| FR-1.6 | Support column-level and table-level `TTL` expressions. | S |
| FR-1.7 | Support `Nested` structures and embedded/flattened value objects. | S |
| FR-1.8 | Support data-skipping indices (`minmax`, `set`, `bloom_filter`, `ngrambf_v1`, `tokenbf_v1`). | S |
| FR-1.9 | Support table `PROJECTION` declarations. | C |
| FR-1.10 | Reject at startup any entity whose mapping is invalid (missing `ORDER BY`, unmappable type, etc.) with an actionable error. | M |

### FR-2 — Type System

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-2.1 | Full signed/unsigned integer coverage: `Int8`–`Int256`, `UInt8`–`UInt256`, with correct unsigned widening. | M |
| FR-2.2 | `Float32/64`, `Decimal(P,S)`, `Decimal32/64/128/256`. | M |
| FR-2.3 | `String`, `FixedString(N)`, `LowCardinality(T)`. | M |
| FR-2.4 | Temporal: `Date`, `Date32`, `DateTime`, `DateTime64(P[,TZ])` with timezone-correct round-tripping. | M |
| FR-2.5 | `UUID`, `IPv4`, `IPv6`, `Bool`. | M |
| FR-2.6 | `Enum8`/`Enum16` ↔ Java `enum`, with ordinal/name/explicit-value strategies. | M |
| FR-2.7 | `Array(T)` (incl. nested arrays), `Map(K,V)`, `Tuple(...)`. | M |
| FR-2.8 | `Nullable(T)` with `Optional<T>` and boxed-type support; correct null vs. default-value distinction. | M |
| FR-2.9 | `JSON` / `Variant` / `Dynamic` types (ClickHouse 24.x+) with Jackson binding. | S |
| FR-2.10 | `AggregateFunction(...)` / `SimpleAggregateFunction(...)` as opaque read-through types. | S |
| FR-2.11 | Geo types (`Point`, `Ring`, `Polygon`, `MultiPolygon`). | C |
| FR-2.12 | Public SPI for user-defined type handlers, registered via `ServiceLoader` and Spring beans. | M |

### FR-3 — Query API

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-3.1 | Fluent builder: `select / where / groupBy / having / orderBy / limit / offset`. | M |
| FR-3.2 | Compile-time metamodel (`PageView_.userId`) generated by an annotation processor. | M |
| FR-3.3 | ClickHouse-specific clauses: `FINAL`, `SAMPLE`, `PREWHERE`, `LIMIT n BY`, `WITH TOTALS`, `WITH ROLLUP/CUBE`, `SETTINGS`. | M |
| FR-3.4 | Aggregate function library incl. `uniq*`, `quantile*`, `topK`, `argMin/argMax`, `sumIf`-style combinators. | M |
| FR-3.5 | Window functions (`OVER (PARTITION BY … ORDER BY … ROWS BETWEEN …)`). | S |
| FR-3.6 | Array functions & `ARRAY JOIN` / `LEFT ARRAY JOIN`. | S |
| FR-3.7 | Explicit joins with join-strategy hints (`GLOBAL`, `ANY`, `ASOF`, `SEMI`, `ANTI`). | S |
| FR-3.8 | CTEs (`WITH x AS (…)`) and subqueries. | S |
| FR-3.9 | Projection into arbitrary DTOs/records, not just entity types. | M |
| FR-3.10 | Native SQL with named parameters, bound server-side, sharing the same row mapper. | M |
| FR-3.11 | Streaming result consumption (`Stream<T>` / `Iterator<T>`) that does not materialise the full result set. | M |
| FR-3.12 | Query-level `SETTINGS` (e.g. `max_execution_time`, `max_memory_usage`) and per-query timeout. | M |
| FR-3.13 | **Parameter binding must be server-side; no string interpolation of user input anywhere.** | M |

### FR-4 — Repositories

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-4.1 | `ClickHouseRepository<T, ID>` with `insert`, `insertAll`, `findAll`, `count`, `stream`. | M |
| FR-4.2 | Derived query methods (`findByTenantIdAndTsBetween(...)`) via Spring Data's `PartTree`. | M |
| FR-4.3 | `@Query` annotation for native SQL on repository methods. | M |
| FR-4.4 | Keyset/cursor pagination (`Slice<T>`); **offset pagination supported but documented as an anti-pattern with a warning log above a configurable offset threshold.** | M |
| FR-4.5 | Custom repository fragment composition (Spring Data `…Impl` convention). | S |
| FR-4.6 | `Sort` and `Limit` parameter support. | M |
| FR-4.7 | Async (`CompletableFuture`) return types. | C |

### FR-5 — Write Path & Ingestion

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-5.1 | Bulk insert via native columnar format (`RowBinary`) — not per-row `PreparedStatement` loops. | M |
| FR-5.2 | Buffered `BatchWriter` with flush-by-size, flush-by-count, and flush-by-interval triggers. | M |
| FR-5.3 | Backpressure when the buffer is full: configurable `BLOCK` / `DROP_OLDEST` / `FAIL` policy. | M |
| FR-5.4 | Server-side `async_insert` support with `wait_for_async_insert` control. | S |
| FR-5.5 | Retry with exponential backoff + jitter on retryable errors; classification of retryable vs. terminal ClickHouse error codes. | M |
| FR-5.6 | Dead-letter callback for permanently failed batches. | M |
| FR-5.7 | Graceful shutdown: flush all buffers within a configurable drain timeout. | M |
| FR-5.8 | Idempotency support via `insert_deduplication_token`. | S |
| FR-5.9 | Explicit `MutationOperations` API for `ALTER TABLE … DELETE/UPDATE`, with async mutation-status polling. | S |
| FR-5.10 | `Lightweight DELETE` (`DELETE FROM … WHERE`) support where the server version allows. | C |

### FR-6 — Schema Management

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-6.1 | Generate `CREATE TABLE` DDL from entity metadata, including engine, `ORDER BY`, `PARTITION BY`, `PRIMARY KEY`, `SAMPLE BY`, TTL, settings. | M |
| FR-6.2 | Engine support: `MergeTree`, `ReplacingMergeTree`, `SummingMergeTree`, `AggregatingMergeTree`, `CollapsingMergeTree`, `VersionedCollapsingMergeTree`, all `Replicated*` variants, `Distributed`, `Memory`, `Null`, `Buffer`, `Dictionary`. | M |
| FR-6.3 | Schema drift detection: compare entity metadata against `system.columns` / `system.tables`, report differences. | M |
| FR-6.4 | Modes: `NONE` (default), `VALIDATE`, `CREATE_IF_MISSING`, `UPDATE`. **`UPDATE` must be impossible to enable by accident and must refuse destructive changes without an explicit force flag.** | M |
| FR-6.5 | Emit migration scripts as files for Flyway/Liquibase rather than applying them. | S |
| FR-6.6 | Materialized-view and dictionary declaration + DDL generation. | S |
| FR-6.7 | Cluster-aware DDL (`ON CLUSTER`). | S |

### FR-7 — Spring Boot Integration

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-7.1 | `clickorm-spring-boot-starter` with full auto-configuration from `clickorm.*` properties. | M |
| FR-7.2 | `@EnableClickHouseRepositories` with base-package scanning; auto-enabled by the starter. | M |
| FR-7.3 | Multiple named ClickHouse datasources/clusters in one application. | S |
| FR-7.4 | Actuator `HealthIndicator` (`SELECT 1` + version + replica lag where available). | M |
| FR-7.5 | Micrometer metrics: query latency/count/errors, rows read/written, batch size histogram, buffer occupancy, retry counts. | M |
| FR-7.6 | Micrometer Tracing / OpenTelemetry spans per query with sanitised SQL tag. | S |
| FR-7.7 | Configuration metadata JSON so IDE auto-complete works on `clickorm.*`. | S |
| FR-7.8 | `clickorm-test` module: Testcontainers support, `@ClickHouseTest` slice annotation, fixture loading, table truncation between tests. | M |
| FR-7.9 | GraalVM native-image hints (reflection/serialization config). | C |
| FR-7.10 | Spring Boot 3.x + Java 17 baseline; Java 21 supported. | M |

## 6. Non-Functional Requirements

| ID | Category | Requirement |
|----|----------|-------------|
| NFR-1 | **Performance — writes** | Bulk insert throughput ≥ 90% of a hand-tuned `clickhouse-java` client benchmark on the same hardware/schema. |
| NFR-2 | **Performance — reads** | Row-mapping overhead ≤ 15% vs. raw `ResultSet` iteration for a 20-column, 1M-row scan. |
| NFR-3 | **Memory** | Streaming reads must be O(1) in result-set size. Batch buffers must be bounded and configurable in bytes, not just rows. |
| NFR-4 | **Startup time** | Metadata resolution for 100 entities ≤ 300ms; no runtime bytecode generation on the hot path. |
| NFR-5 | **Compatibility** | Support ClickHouse LTS versions from 24.3 onward; CI matrix across at least 3 server versions. |
| NFR-6 | **Security** | 100% server-side parameter binding. SQL in logs/traces must be parameterised, never interpolated. Credentials never logged. TLS + mTLS support. |
| NFR-7 | **Reliability** | No data loss on graceful shutdown. Failed batches must be surfaced, never silently dropped. |
| NFR-8 | **Testability** | ≥ 85% line coverage on `clickorm-core`; every FR with an integration test against a real ClickHouse container. |
| NFR-9 | **Modularity** | `clickorm-core` must have **zero Spring dependencies** — usable from plain Java, Quarkus, or Micronaut. |
| NFR-10 | **Observability** | Every query and batch carries a correlation ID propagated to ClickHouse via `query_id`, enabling joins against `system.query_log`. |
| NFR-11 | **Documentation** | Every public API has Javadoc; a documentation site with a "Coming from JPA" migration guide and an explicit "what we deliberately don't do" page. |
| NFR-12 | **API stability** | Semantic versioning. Public API frozen at 1.0; breaking changes require a major bump and a deprecation cycle of at least one minor release. |
| NFR-13 | **Licensing** | Apache 2.0. No GPL/AGPL transitive dependencies. |
| NFR-14 | **Build** | Reproducible Gradle/Maven build, published to Maven Central, signed artifacts, SBOM generated. |

## 7. Key Assumptions

1. The official `clickhouse-java` client (client-v2) is the primary transport; JDBC is a
   supported alternative for tooling compatibility.
2. Users own their schema lifecycle in production. ClickORM's `UPDATE` mode is a
   development convenience only.
3. Target users are already ClickHouse-literate. ClickORM reduces boilerplate; it is not
   a ClickHouse tutorial.
4. Analytical read workloads dominate; write workloads are append-heavy and batched.

## 8. Risks

| ID | Risk | Impact | Mitigation |
|----|------|--------|------------|
| R-1 | **Abstraction seduces users into OLTP patterns** (row-at-a-time inserts, offset pagination, implicit joins). | High | Metrics + warning logs on anti-patterns; docs lead with constraints; no convenience API for row-level mutation. |
| R-2 | ClickHouse type system evolves fast (`JSON`, `Variant`, `Dynamic`). | Med | Pluggable type-handler SPI (FR-2.12); version-gated feature detection at startup. |
| R-3 | Performance worse than raw JDBC → project loses its reason to exist. | High | JMH benchmark suite in CI from Phase 1; NFR-1/NFR-2 are release gates, not goals. |
| R-4 | Solo-maintainer burnout / bus factor of 1. | High | Modular architecture, contribution guide from Phase 0, aggressive scope discipline. |
| R-5 | Spring Data internal APIs change between minor versions. | Med | Thin adapter layer isolating Spring Data SPI usage; core is Spring-free (NFR-9). |
| R-6 | Annotation processor complexity (incremental compilation, IDE support). | Med | Metamodel generation is optional — the string-based API must remain fully functional without it. |
| R-7 | Competing with an official ClickHouse Inc. offering appearing later. | Med | Stay close to the official Java client; position as complementary, not a fork. |

## 9. Success Criteria

**v0.1 (Alpha):** A Spring Boot app can define an entity, create the table, insert 1M rows
in batches, and run a typed aggregation query — end to end, in under 30 lines of user code.

**v1.0 (GA):**
- All **M**-priority FRs implemented and integration-tested.
- NFR-1 and NFR-2 verified by a public benchmark with reproducible results.
- ≥ 3 external production adopters willing to be named.
- Documentation site live, including the "Coming from JPA" guide.
- Zero known data-loss defects.

## 10. Open Questions

| # | Question | Owner | Needed by |
|---|----------|-------|-----------|
| Q-1 | Transport default: client-v2 native protocol or JDBC? (perf vs. tooling compat) | | Phase 0 |
| Q-2 | Do we ship the annotation processor in v1.0 or defer the metamodel to 1.1? | | Phase 1 |
| Q-3 | Kotlin support: first-class DSL or "it just works via Java interop"? | | Phase 4 |
| Q-4 | Build tool: Gradle or Maven for the project itself? | | Phase 0 |
| Q-5 | Do we depend on `spring-data-commons` or implement repository proxying ourselves? | | Phase 4 |
| Q-6 | Naming/branding — is "ClickORM" viable given it contradicts P6 ("no relational modelling")? | | Phase 0 |

---

## Appendix A — Requirements Traceability Summary

| Area | Must | Should | Could | Total |
|------|-----:|-------:|------:|------:|
| FR-1 Entity Mapping | 6 | 3 | 1 | 10 |
| FR-2 Type System | 9 | 2 | 1 | 12 |
| FR-3 Query API | 9 | 4 | 0 | 13 |
| FR-4 Repositories | 5 | 1 | 1 | 7 |
| FR-5 Write Path | 7 | 2 | 1 | 10 |
| FR-6 Schema | 4 | 3 | 0 | 7 |
| FR-7 Spring Integration | 6 | 3 | 1 | 10 |
| **Total** | **46** | **18** | **5** | **69** |

46 Must-have requirements is the v1.0 critical path. See `02-PHASED-PLAN.md` for sequencing.
