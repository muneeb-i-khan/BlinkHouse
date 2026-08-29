# ClickORM — Phased Build Plan

**Companion docs:** `01-REQUIREMENTS.md`, `03-HLD.md`, `04-LLD.md`
**Document version:** 1.0

---

## 0. How to read this plan

Each phase is a **shippable increment** with its own version tag, exit criteria, and demo.
Nothing in a later phase is a prerequisite for an earlier one — if you stop after Phase 3,
you still have a useful library.

**Effort notation:** `[FT]` = full-time engineer-weeks, `[PT]` = calendar weeks at ~10h/week.
Both are given because most OSS projects of this kind are built part-time.

| Phase | Ships | FT weeks | PT calendar |
|-------|-------|---------:|------------:|
| 0 — Foundations & Spike | — | 2 | 5 |
| 1 — Core Mapping & Read Path | `v0.1.0` | 5 | 13 |
| 2 — Write Path & Ingestion | `v0.2.0` | 4 | 10 |
| 3 — Schema & DDL | `v0.3.0` | 4 | 10 |
| 4 — Spring Boot Starter & Repositories | `v0.4.0` | 6 | 15 |
| 5 — Typed Query Builder & Metamodel | `v0.5.0` | 6 | 15 |
| 6 — Observability & Resilience | `v0.6.0` | 3 | 8 |
| 7 — Advanced ClickHouse Features | `v0.9.0` | 5 | 13 |
| 8 — Hardening, Docs, GA | `v1.0.0` | 5 | 13 |
| **Total** | | **40** | **~100 (≈ 2 yrs PT)** |

> **Reality check.** 40 full-time engineer-weeks ≈ 9–10 months for one person, or ~5 months
> for a focused pair. Part-time solo, this is a two-year project. **The single most
> valuable decision you can make is to cut Phases 5 and 7 from v1.0** and ship a smaller,
> excellent library at ~26 FT-weeks. That option is called out in §11.

---

## Phase 0 — Foundations & Spike
**Goal:** Prove the riskiest technical assumptions before writing framework code.
**Duration:** 2 FT-weeks · **No release**
**Status:** Complete

### Build
- [ ] Repo scaffolding: multi-module build, CI (GitHub Actions), Testcontainers harness, code style, license headers, `CONTRIBUTING.md`.
- [x] **Spike A — Transport bake-off.** Insert 100k rows and scan via (a) `clickhouse-jdbc`, (b) `client-v2`, (c) HTTP + `RowBinary`. Results: ~2.9M rows/s (raw HTTP) vs ~600k (client-v2) vs ~430k (JDBC). *Resolves Q-1.* → See `docs/adr/ADR-04-transport.md`.
- [x] **Spike B — Type round-trip.** 12 hardest types implemented in `ChOutputStream`/`ChInputStream` + 12 `TypeHandler` impls. 12/13 tests pass; JSON skipped (internal binary format, not simple LEB128 — documented in test). *Resolves Q-4.*
- [x] **Spike C — Spring Data SPI.** `@EnableClickHouseRepositories`, `ClickHouseRepositoryFactoryBean`, `ClickHouseRepositoryFactory`, `SimpleClickHouseRepository`. Fragment composition (`PageViewRepositoryImpl`) dispatched correctly through the proxy. 3/3 tests pass. *Resolves Q-5.*
- [x] JMH benchmark module compiles and is suppressed from full runs in CI (NFR-1 baseline recorded in ADR-04; CI gate enforced from Phase 2 onward).

### Exit criteria
- [x] Transport decision recorded in an ADR with benchmark evidence. → `docs/adr/ADR-04-transport.md`
- [x] All 12 hard types round-trip correctly (or known-unsupported list documented). JSON skipped with documented reason.
- [x] ADR log established (`/docs/adr/`).
- [x] CI pipeline configured (`.github/workflows/ci.yml`): build + unit tests, integration tests across ClickHouse 24.3/24.8/25.1, benchmark compile check.
- [x] Checkstyle enforced at `mvn verify`; existing source clean.
- [x] Shared Testcontainers base class (`ClickHouseContainerExtension`) — one container per JVM, image overridable via `BH_CLICKHOUSE_IMAGE`.
- [x] `CONTRIBUTING.md` written.
- [x] `.editorconfig` added.

### Resolved questions
- **Q-1 (Transport):** Split transport — raw HTTP + RowBinary for writes, client-v2 for reads. See `docs/adr/ADR-04-transport.md`.
- **Q-5 (Spring Data SPI):** Depend on `spring-data-commons`. `RepositoryFactoryBean` SPI is viable; fragment composition works end-to-end.

### Decision: Split transport (from ADR-04)
Raw HTTP + RowBinary for writes (BatchWriter), client-v2 for reads (ChTemplate). JDBC retained as optional compatibility escape hatch only. NFR-1 baseline locked at ≥ 2.6M rows/sec.

### Risks addressed
R-3 (perf), R-5 (Spring Data coupling), Q-1, Q-4, Q-5.

---

## Phase 1 — Core Mapping & Read Path → `v0.1.0`
**Goal:** Define an entity, run a query, get typed objects back. Zero Spring.
**Duration:** 5 FT-weeks

### Build
1. **Annotation set (v1):** `@ChTable`, `@ChColumn`, `@ChIgnore`, `@ChEngine`.
2. **Metadata layer:** `EntityMetadata`, `ColumnMetadata`, reflective `EntityMetadataResolver` with a startup-time cache, naming strategies (`SnakeCase`, `AsIs`, custom SPI).
3. **Type system:** `TypeHandler<J, C>` SPI + registry; handlers for all **FR-2.1 → FR-2.8** types. `ClickHouseType` parser/renderer (parse `LowCardinality(Nullable(Decimal(18,4)))` into a tree and render it back).
4. **Connection layer:** `ChConnectionProvider` SPI, pooled implementation, `ChClientOptions` (timeouts, compression, TLS, settings).
5. **Read execution:** `ChTemplate` — the Spring-free core operations interface. `query(sql, params, RowMapper)`, `stream(...)`, `queryForObject(...)`.
6. **Row mapping:** `RowMapper<T>` SPI; `EntityRowMapper` (entity-bound), `RecordRowMapper` (Java records via canonical constructor), `SingleColumnRowMapper`, `MapRowMapper`.
7. **Streaming:** `Stream<T>` backed by a cursor, closing the underlying response on `close()`; try-with-resources enforced.
8. **Parameter binding:** named parameters, server-side binding only (NFR-6).
9. **Exception translation:** ClickHouse error codes → a typed `ChException` hierarchy (`ChSyntaxException`, `ChTimeoutException`, `ChMemoryLimitException`, `ChConnectionException`, `ChTooManyPartsException`, …).

### Exit criteria (demo)
```java
var template = ChTemplate.builder().url("http://localhost:8123").build();
List<PageView> rows = template.query(
    "SELECT * FROM page_views WHERE tenant_id = :t LIMIT 100",
    Map.of("t", 42),
    RowMappers.forEntity(PageView.class));
```
- NFR-2 met (read mapping overhead ≤ 15%) — verified by JMH.
- All FR-2 Must types integration-tested against a live container.
- ≥ 85% coverage on `clickorm-core`.

### Deliverables
`clickorm-core` published to Maven Central as `0.1.0`. Zero Spring dependencies (NFR-9).

---

## Phase 2 — Write Path & Ingestion → `v0.2.0`
**Goal:** Ingest at production throughput without the user writing buffer code.
**Duration:** 4 FT-weeks
**Status:** Complete

### Build
1. [x] **Columnar serialisation:** `RowBinaryWriter` — serialise a `List<T>` directly into ClickHouse's binary format via the type handlers. **No per-row `PreparedStatement`.**
2. [x] **`insert(List<T>)`** on `ChTemplate` — synchronous bulk path.
3. [x] **`BatchWriter<T>`:** bounded ring buffer, flush triggers (row count / byte size / interval), background flusher threads, `close()` drain.
4. [x] **Backpressure policies:** `BLOCK`, `DROP_OLDEST`, `FAIL` (FR-5.3).
5. [x] **Retry engine:** error-code classification table (retryable: 202, 252, 209, 210, 203, 159, 999; terminal: 47, 53, 60, 62, 81, 192), exponential backoff with full jitter, max attempts. MEMORY_LIMIT_EXCEEDED (241) and TOO_MANY_PARTS (252) halve batch size on retry.
6. [x] **Dead-letter hook:** `BatchFailureHandler` callback receiving the failed rows + cause.
7. [x] **`async_insert` support** with `wait_for_async_insert` flag (FR-5.4).
8. [x] **Shutdown semantics:** JVM shutdown hook + explicit drain timeout; guarantee of no silent loss (NFR-7). `BatchWriterConfig.drainTimeout` default 30 s.
9. [x] **Anti-pattern instrumentation:** single-row `insert(T)` increments a `clickorm.insert.singlerow` counter and logs at WARN once per minute per table (P2 / R-1).

### Supporting classes
- `ChException` hierarchy: `ChSyntaxException`, `ChTimeoutException`, `ChMemoryLimitException`, `ChConnectionException`, `ChTooManyPartsException`, `ChBufferFullException`, `ChBackpressureException`
- `ChExceptionTranslator` — parses HTTP error bodies to typed exceptions
- `ErrorClassifier` — RETRYABLE / RETRYABLE_HALVE_BATCH / TERMINAL
- `RetryPolicy` (record) — exponential backoff with full jitter
- `FlushTrigger` — three-condition flush (rows, bytes, interval)
- `BatchWriterStats` — LongAdder-based counters, `snapshot()` for observability
- `EntityMetadata<T>` + `ColumnMetadata<T>` + `ValueAccessor<T>` — metadata model
- `EntityMetadataFactory` — reflective resolver (Phase 1 will promote to LambdaMetafactory)
- `TypeRegistry` — type handler registry, pre-loaded with built-in handlers
- Annotations: `@ChTable`, `@ChColumn`, `@ChIgnore`

### Exit criteria
- [x] Integration tests: `RowBinaryWriterIT` (3), `BatchWriterIT` (5), `ChTemplateAntiPatternIT` (2) — all green.
- [x] Unit tests: `ErrorClassifierTest` (13), `RetryPolicyTest` (4) — all green.
- [x] 0 Checkstyle violations.
- [x] Graceful-shutdown test: 1 000 rows, `close()`, all rows land. Dead-letter handler verified.
- [ ] NFR-1 JMH gate (≥ 2.6M rows/sec) — deferred to Phase 6 CI gate; benchmark module already wired.
- [ ] Chaos test (container kill mid-ingest) — manual validation; automated chaos test deferred to Phase 6.

---

## Phase 3 — Schema & DDL → `v0.3.0`
**Goal:** Schema as code, with migrations that a DBA would sign off on.
**Duration:** 4 FT-weeks

### Build
1. **Engine model:** typed engine descriptors for the full FR-6.2 list, each with its own required/optional parameters and validation (e.g. `ReplacingMergeTree` version column must be a valid type; `SummingMergeTree` columns must be numeric).
2. **DDL generator:** `CREATE TABLE` including `ENGINE`, `ORDER BY`, `PARTITION BY`, `PRIMARY KEY`, `SAMPLE BY`, `TTL`, codecs, `SETTINGS`, `ON CLUSTER`.
3. **Extended annotations:** `@ChSkipIndex`, `@ChTtl`, `@ChCodec`, `@ChNested`, `@ChProjection`, `@ChSettings`.
4. **Schema introspection:** read `system.tables`, `system.columns`, `system.data_skipping_indices` into a `LiveSchema` model.
5. **Differ:** `SchemaDiff` producing a typed change list (`AddColumn`, `DropColumn`, `ModifyColumnType`, `AddIndex`, `EngineMismatch`, `OrderByMismatch`) with a **destructive/non-destructive classification**.
6. **`SchemaMode` state machine:** `NONE` (default) · `VALIDATE` (fail fast on drift) · `CREATE_IF_MISSING` · `UPDATE`. `UPDATE` refuses destructive changes unless `allowDestructive=true` is *separately* set (FR-6.4).
7. **Script emitter:** write the diff as timestamped `.sql` files for Flyway/Liquibase instead of executing (FR-6.5).
8. **Startup validation report:** a readable table of drift printed on `VALIDATE` failure, not a stack trace.

### Exit criteria
- Round-trip test: generate DDL → create table → introspect → diff → **zero differences** for every supported engine.
- Destructive-change guard proven by test (attempt to drop a column in `UPDATE` mode fails without the force flag).
- Flyway interop example in the repo.

---

## Phase 4 — Spring Boot Starter & Repositories → `v0.4.0`
**Goal:** The experience a Spring developer actually expects. This is the adoption phase.
**Duration:** 6 FT-weeks

### Build
1. **`clickorm-spring`:** `ChTemplate` bean wiring, exception translation into Spring's `DataAccessException` hierarchy, `@EnableClickHouseRepositories`.
2. **Repository infrastructure:** `ClickHouseRepositoryFactoryBean`, `ClickHouseRepositoryFactory`, `SimpleClickHouseRepository` base implementation, repository interface scanning.
3. **Derived query methods:** `PartTree` parsing → `QueryModel`; supported keywords: `And`, `Or`, `Between`, `LessThan`, `GreaterThan`, `In`, `NotIn`, `Like`, `StartingWith`, `IsNull`, `True/False`, `OrderBy`, `Top/First`, `Distinct`, `Count`, `Exists`.
4. **`@Query`** annotation (native SQL) with named + positional parameters and SpEL-free parameter binding.
5. **Pagination:** `Slice<T>` keyset pagination as the primary API; `Page<T>` offset pagination supported but emitting a warning above a configured offset threshold (FR-4.4 / R-1).
6. **Custom fragments:** `XxxRepositoryCustom` + `XxxRepositoryImpl` convention.
7. **`clickorm-spring-boot-starter`:** `ClickOrmAutoConfiguration`, `ClickOrmProperties` (`clickorm.*`), `spring-configuration-metadata.json`, sensible production defaults.
8. **Multi-datasource:** `@ChDatasource("analytics")` qualifier support for multiple clusters (FR-7.3).
9. **`clickorm-test`:** `@ClickHouseTest` slice annotation, Testcontainers auto-start, per-test `TRUNCATE`, fixture loading from CSV/JSON.

### Exit criteria (demo)
```java
public interface PageViewRepository extends ClickHouseRepository<PageView, UUID> {
    Slice<PageView> findByTenantIdAndTsBetween(int tenantId, Instant from, Instant to, Cursor c);

    @Query("SELECT toStartOfHour(ts) h, uniq(user_id) u FROM page_views " +
           "WHERE tenant_id = :t GROUP BY h ORDER BY h")
    List<HourlyUniques> hourlyUniques(@Param("t") int tenantId);
}
```
- A Spring Boot app with only `application.yml` + entity + repository works with **zero Java configuration**.
- `@ClickHouseTest` slice boots in under 10 seconds with a reused container.

### Why this is the biggest phase
Spring Data's repository SPI is powerful but under-documented; `PartTree` translation to a
non-relational dialect has genuine edge cases (nullability, `IgnoreCase`, nested paths).
Budget contingency here specifically.

---

## Phase 5 — Typed Query Builder & Metamodel → `v0.5.0`
**Goal:** Compile-time safety and analytics-native query construction.
**Duration:** 6 FT-weeks

### Build
1. **AST:** `Expression` / `Predicate` / `SelectStatement` node model + SQL renderer with correct precedence and quoting.
2. **Fluent DSL:** `ChQuery.from(PageView_.TABLE).select(...).prewhere(...).where(...).groupBy(...).having(...).orderBy(...).limitBy(...).settings(...)`.
3. **Annotation processor** (`clickorm-processor`): generates `PageView_` metamodel classes with typed `Column<PageView, Instant> TS` fields. **Must be optional** — a string-based `col("ts")` path stays fully supported (R-6).
4. **Function library:** aggregates (`uniq`, `uniqExact`, `uniqCombined`, `quantile*`, `topK`, `argMin/argMax`, `median`), `-If`/`-Array`/`-Merge`/`-State` combinator support, date functions (`toStartOfX`, `toRelativeX`), array functions, string functions.
5. **ClickHouse clauses:** `FINAL`, `SAMPLE`, `PREWHERE`, `LIMIT n BY`, `WITH TOTALS`, `WITH ROLLUP/CUBE`, per-query `SETTINGS`.
6. **Window functions** and `OVER` clause builder.
7. **Joins** with explicit strategy hints (`ANY`, `ALL`, `ASOF`, `SEMI`, `ANTI`, `GLOBAL`) — always explicit, never inferred (P6).
8. **CTEs and subqueries.**
9. **DTO projection:** `.into(HourlyUniques.class)` with record support.

### Exit criteria
- 30 representative analytical queries from real dashboards expressible in the DSL without dropping to raw SQL.
- Renaming an entity field breaks compilation in every query referencing it.
- Generated SQL is snapshot-tested (golden files) across the version matrix.

---

## Phase 6 — Observability & Resilience → `v0.6.0`
**Goal:** Operable in production by people who didn't write it.
**Duration:** 3 FT-weeks

### Build
1. **Micrometer metrics:** query timer (tagged by repo/method/table), rows read/written, batch size histogram, buffer occupancy gauge, retry/dead-letter counters, connection pool gauges.
2. **`query_id` propagation:** every query carries a generated, correlatable `query_id` so users can join application traces to `system.query_log` (NFR-10).
3. **Micrometer Tracing / OTel spans** with sanitised, parameterised SQL tags (never interpolated values — NFR-6).
4. **Actuator `HealthIndicator`:** connectivity, server version, and — for replicated setups — `system.replicas` lag.
5. **Structured slow-query logging** with configurable threshold.
6. **Circuit breaker** on the write path (fail fast when ClickHouse is down rather than accumulating unbounded buffers).
7. **Diagnostics endpoint:** `/actuator/clickorm` dumping resolved entity metadata, schema drift status, and buffer state.

### Exit criteria
- A Grafana dashboard JSON shipped in the repo, working against the emitted metrics.
- Runbook page: "ClickORM is slow / dropping data — what to check."

---

## Phase 7 — Advanced ClickHouse Features → `v0.9.0`
**Goal:** Cover the features that make ClickHouse ClickHouse.
**Duration:** 5 FT-weeks

### Build
1. **Materialized views:** `@ChMaterializedView` declaration, DDL generation, target-table handling, `POPULATE` control.
2. **Dictionaries:** `@ChDictionary` declaration, DDL generation, `dictGet*` support in the DSL (this is the ClickHouse-native answer to `@ManyToOne`, without the ORM lie).
3. **Distributed tables:** local + distributed table pairs, sharding key config, `ON CLUSTER` DDL, `GLOBAL IN`/`GLOBAL JOIN`.
4. **`AggregateFunction` / `SimpleAggregateFunction`** columns with `-State`/`-Merge` support for pre-aggregated tables.
5. **Newer type support:** `JSON`, `Variant`, `Dynamic` with Jackson binding and version-gated feature detection (R-2).
6. **`MutationOperations`:** explicit `ALTER TABLE … DELETE/UPDATE` with async mutation-status polling against `system.mutations`; lightweight `DELETE` where supported.
7. **Geo types** (FR-2.11).
8. **`OPTIMIZE TABLE … FINAL`** helper with loud documentation about its cost.

### Exit criteria
- Multi-node cluster integration test (3-node ClickHouse Keeper + replicated setup) in CI.
- A worked example: raw events table → MV → aggregated table → typed query over `-Merge` states.

---

## Phase 8 — Hardening, Docs & GA → `v1.0.0`
**Goal:** Earn the 1.0 and the API-stability promise.
**Duration:** 5 FT-weeks

### Build
1. **API review & freeze:** every public class audited; internals moved to `…internal.*` packages; `@ApiStatus`-style annotations applied. This is a real, week-long exercise.
2. **Version compatibility matrix:** CI across ClickHouse 24.3 LTS / 24.8 LTS / latest, Spring Boot 3.2/3.3/3.4, Java 17/21.
3. **Documentation site:** getting started, entity mapping reference, query DSL cookbook, ingestion tuning guide, schema migration guide, **"Coming from JPA"**, and **"What ClickORM deliberately does not do"** (P1 — this page is a feature).
4. **Benchmarks published** with reproducible harness (NFR-1, NFR-2).
5. **Reference application:** a real analytics service (event ingest + dashboard API) in the repo.
6. **Security review:** SQL injection audit of every code path that builds SQL; dependency CVE scan; SBOM.
7. **GraalVM native-image hints** (FR-7.9).
8. **Release engineering:** signed artifacts, Maven Central, changelog, semver policy, deprecation policy.
9. **Community:** issue templates, `CODEOWNERS`, roadmap page, at least 3 named production adopters (§9 of requirements).

### Exit criteria
All v1.0 success criteria from `01-REQUIREMENTS.md` §9 met.

---

## 9. Cross-Cutting Workstreams

These run continuously from Phase 1, not as separate phases. Budget ~20% of each phase.

| Workstream | Detail |
|------------|--------|
| **Testing** | Unit + Testcontainers integration per phase. Golden-file SQL snapshot tests from Phase 3. Chaos tests from Phase 2. |
| **Benchmarking** | JMH suite from Phase 0; NFR-1/NFR-2 are CI gates, and a regression blocks merge. |
| **Documentation** | Javadoc written with the code, not after. Each phase ships its reference page. |
| **ADRs** | Every irreversible decision recorded. Especially transport, metamodel, Spring Data coupling. |
| **Community** | Public repo from day one; alpha adopters recruited from Phase 2 onward for real-world feedback. |

## 10. Dependency Graph

```
Phase 0 ──► Phase 1 ──┬──► Phase 2 ──┬──► Phase 6
                      │              │
                      ├──► Phase 3 ──┤
                      │              │
                      └──► Phase 5 ──┤
                                     │
              Phase 1,2,3 ──► Phase 4 ──► Phase 7 ──► Phase 8
```
- **Phase 4 requires 1, 2, 3.** The starter needs read, write, and schema to be meaningful.
- **Phase 5 only requires 1.** It can be developed in parallel by a second contributor.
- **Phase 6 only requires 2.** Also parallelisable.
- **Phase 7 requires 4** (annotations + starter wiring).

## 11. Recommended Cut for a Small Team — "Lean v1.0"

If you are one or two people, **do not build all eight phases before 1.0.** Ship this instead:

| Include | Defer to 1.x |
|---------|--------------|
| Phase 0, 1, 2, 3, 4, 6 | Phase 5 (typed metamodel → 1.1) |
| Phase 8 (trimmed) | Phase 7 (advanced features → 1.2) |

**Lean v1.0 ≈ 26 FT-weeks (~6 months full-time, ~14 months part-time).**

The string-based query API plus `@Query` native SQL covers the 80% case. Compile-time
metamodel safety is genuinely valuable but it is the single most expensive feature per unit
of adoption, and it is purely additive — shipping it in 1.1 breaks nothing.

Likewise, materialized views and distributed tables matter to a minority of users at first
and can follow real demand rather than speculation.

## 12. Milestone Calendar (illustrative, 2 engineers full-time)

| Month | Milestone |
|-------|-----------|
| 1 | Phase 0 complete, ADRs signed, `v0.1.0` in progress |
| 2 | `v0.1.0` released — core read path |
| 3 | `v0.2.0` — ingestion; first alpha adopter onboarded |
| 4 | `v0.3.0` — schema; Phase 5 begins in parallel |
| 5–6 | `v0.4.0` — starter + repositories (the adoption inflection point) |
| 7 | `v0.5.0` + `v0.6.0` — DSL and observability |
| 8 | `v0.9.0` — advanced features |
| 9–10 | `v1.0.0` — hardening, docs, benchmarks, GA |

## 13. Go / No-Go Gates

Kill or pivot the project if:

- **After Phase 0:** the transport spike shows framework overhead cannot get within 15% of raw client performance. *The library's reason to exist is gone.*
- **After Phase 2:** ingestion throughput fails NFR-1 after two optimisation attempts.
- **After Phase 4:** no external user adopts the starter within 3 months of release. *Either the API is wrong or the need was overestimated — get feedback before building Phases 5 and 7.*
- **Any phase:** ClickHouse Inc. ships an official Spring integration. Reassess as a contribution target rather than a competitor (R-7).
