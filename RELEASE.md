# BlinkHouse v1.0.0 — General Availability

First stable release of BlinkHouse, a ClickHouse-native persistence framework for Java/Spring Boot. API is frozen on all public types outside `*.internal.*` packages.

---

## What's in this release

**Read & write path**
- `ChTemplate` — the central facade: typed queries, native SQL, streaming reads, single-row and batch inserts
- `BatchWriter<T>` — high-throughput buffered ingest with bounded queue, three flush triggers (rows / bytes / elapsed), configurable backpressure (`BLOCK` / `DROP_OLDEST` / `FAIL`), automatic retry with exponential backoff and dead-letter dispatch
- `RowBinaryWriter` — zero-copy columnar serialisation direct to the wire format; no intermediate `Object[]` or JDBC batch

**Type system**
- Full ClickHouse type coverage: `UInt8/16/32/64`, `Int8–256`, `Float32/64`, `Decimal`, `String`, `FixedString`, `UUID`, `DateTime`/`DateTime64`/`Date`/`Date32`, `IPv4`/`IPv6`, `Enum8/16`, `LowCardinality`, `Nullable`, `Array`, `Map`, `Tuple`, geo types (`Point`, `Ring`, `Polygon`, `MultiPolygon`), `AggregateFunction`
- All handlers round-trip tested; `UUID` byte-order swap and `DateTime64` timezone-from-type correctness verified

**Schema & DDL**
- `SchemaManager` with four modes: `NONE`, `VALIDATE`, `CREATE_IF_MISSING`, `UPDATE`
- Destructive changes require two independent opt-ins (no `ddl-auto=update` footgun)
- DDL covers engines, `ORDER BY`, `PARTITION BY`, `TTL`, codecs, skip indexes, `ON CLUSTER`

**Spring Boot Starter**
- Zero-config autoconfiguration — `application.yml` + entity + repository is all you need
- Spring Data repository support: derived query methods, `@Query` native SQL, keyset pagination (`Slice<T>`) as primary, offset with threshold warning
- `@BlinkHouseTest` test slice with Testcontainers, boots in under 10 s

**Query DSL**
- `ChQuery` fluent builder: `FINAL`, `SAMPLE`, `PREWHERE`, `LIMIT n BY`, `WITH TOTALS`, window functions, joins, CTEs
- Full `Functions` library including ClickHouse-specific aggregates (`uniq*`, `quantile*`, `topK`, `argMin/argMax`), `-Merge` combinators, date/array/string functions, geo functions (`pointInPolygon`, `geoDistance`), and dictionary functions (`dictGet`, `dictGetOrDefault`)
- All user values flow through `ParameterRef` server-side binding — no string interpolation anywhere in the render path

**Advanced ClickHouse features**
- `@ChMaterializedView` and `@ChDictionary` annotations with full DDL generation
- Distributed table engine wiring
- `MutationOperations`: `ALTER TABLE … DELETE/UPDATE WHERE …`
- `ChTemplate.optimize()`: `OPTIMIZE TABLE … FINAL [ON CLUSTER]` for forcing merges

**Observability**
- `ChMetrics` / `ChTracer` SPI with Micrometer and OpenTelemetry implementations
- Correlatable `query_id` format: `blinkhouse-{appName}-{uuid}` — joinable against `system.query_log`
- Grafana dashboard JSON and operations runbook included in the repo

**Production-grade connection pooling**
- Apache HttpClient 5 `PoolingHttpClientConnectionManager` replacing the bare JDK `HttpClient`
- One pool per `ChTemplate`, shared with all its `BatchWriter` children — no per-writer allocation
- Background idle-connection evictor; inactivity validation before lease
- Fully tunable via `clickhouse.pool.*` in `application.yml`; `ChTemplate` implements `Closeable` for clean Spring shutdown

**Hardening & GA**
- `@BlinkHouseApi` / `@Internal` stability annotations with ArchUnit enforcement in CI
- SQL injection audit test suite — user values provably never reach the SQL string
- GraalVM native-image hints for all 40 registered classes
- CI matrix: Java 17 × 21, ClickHouse 24.3 / 24.8 / 25.1
- Reference analytics service (`examples/analytics-service/`)
- "Coming from JPA" migration guide (`docs/05-COMING-FROM-JPA.md`)

---

## What this release deliberately does not do
- No transactions, no rollback, no unit of work
- No lazy loading, no dirty-checking, no identity map
- No relational navigation (`@OneToMany`, joins-as-relations)
- No silent drop path — every row ends in either "acked by ClickHouse" or "handed to the failure handler"

---

**Test coverage:** 132 unit tests · 29 integration tests (Testcontainers)  
**Compatibility:** Java 17+, Spring Boot 3.2–3.4, ClickHouse 24.3+
