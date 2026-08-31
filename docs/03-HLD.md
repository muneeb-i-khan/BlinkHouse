# ClickORM — High-Level Design (HLD)

**Companion docs:** `01-REQUIREMENTS.md`, `02-PHASED-PLAN.md`, `04-LLD.md`
**Document version:** 1.0
**Audience:** Architects, reviewers, contributors evaluating the shape of the system

---

## 1. Architectural Goals

| Goal | Driver | Manifestation in design |
|------|--------|-------------------------|
| Framework-agnostic core | NFR-9 | `clickorm-core` has zero Spring imports; Spring is a thin adapter module. |
| Zero hot-path reflection | NFR-4, NFR-2 | All reflection resolved once at startup into immutable metadata + `MethodHandle`/`LambdaMetafactory` accessors. |
| Columnar-first I/O | NFR-1 | `RowBinary` block serialisation, never per-row `PreparedStatement`. |
| Pluggable everything | R-2, FR-2.12 | SPI at every boundary: transport, type handler, naming, dialect, row mapper. |
| Honest semantics | P1 | No transaction manager, no unit of work, no identity map, no lazy proxies. |
| Escape hatch parity | P4 | Native SQL shares the same binding, mapping, metrics, and exception translation as the DSL. |

## 2. System Context

```
┌────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                     │
│                                                                │
│   @Service                @RestController      Ingest worker   │
│       │                        │                     │         │
│       ▼                        ▼                     ▼         │
│  ┌──────────────┐      ┌──────────────┐     ┌──────────────┐   │
│  │  Repository  │      │   ChQuery    │     │ BatchWriter  │   │
│  │  interfaces  │      │  typed DSL   │     │              │   │
│  └──────┬───────┘      └──────┬───────┘     └──────┬───────┘   │
│         └──────────────────────┼────────────────────┘          │
│                                ▼                               │
│                        ┌───────────────┐                       │
│                        │  ChTemplate   │  ← core facade        │
│                        └───────┬───────┘                       │
└────────────────────────────────┼───────────────────────────────┘
                                 │ HTTP / native TCP, RowBinary
                                 ▼
                  ┌──────────────────────────────┐
                  │      ClickHouse cluster      │
                  │  shards · replicas · Keeper  │
                  └──────────────────────────────┘
```

External touchpoints: Micrometer (metrics), OpenTelemetry (traces), Flyway/Liquibase
(migration scripts), Testcontainers (tests), Maven Central (distribution).

## 3. Module Decomposition

```
clickorm/
├── clickorm-core                 ← no Spring, no Jakarta. Pure Java 17.
│   ├── annotation/               @ChTable, @ChColumn, @ChEngine, …
│   ├── metadata/                 EntityMetadata, resolver, naming strategies
│   ├── type/                     ClickHouseType model, TypeHandler SPI + registry
│   ├── connection/               ChConnectionProvider SPI, pooling, options
│   ├── protocol/                 RowBinary reader/writer, block codec
│   ├── query/                    AST, renderer, parameter binding
│   ├── mapping/                  RowMapper SPI + built-ins
│   ├── template/                 ChTemplate — the core facade
│   ├── write/                    BatchWriter, retry, backpressure, dead-letter
│   ├── schema/                   DDL generator, introspector, differ, migrator
│   └── exception/                ChException hierarchy + error-code translation
│
├── clickorm-processor            ← annotation processor → Xxx_ metamodel  (optional)
│
├── clickorm-spring               ← Spring Data adapter
│   ├── repository/               factory bean, proxy, PartTree translation
│   ├── config/                   @EnableClickHouseRepositories
│   └── support/                  DataAccessException translation
│
├── clickorm-spring-boot-starter  ← auto-config + properties + actuator
│
├── clickorm-test                 ← @ClickHouseTest, Testcontainers, fixtures
│
├── clickorm-benchmark            ← JMH (not published)
│
└── examples/                     reference analytics application
```

**Dependency rule (enforced by ArchUnit in CI):**
`core` → nothing. `processor` → `core` (compile only). `spring` → `core` + spring-data-commons.
`starter` → `spring`. Nothing depends on `starter`. **No module may depend on a module to its left.**

## 4. Layered Architecture

```
┌───────────────────────────────────────────────────────────────┐
│ L5  User API      Repositories · ChQuery DSL · BatchWriter    │
├───────────────────────────────────────────────────────────────┤
│ L4  Orchestration ChTemplate — bind, execute, map, instrument │
├───────────────────────────────────────────────────────────────┤
│ L3  Translation   AST→SQL · PartTree→AST · Entity→DDL         │
├───────────────────────────────────────────────────────────────┤
│ L2  Metadata      EntityMetadata · TypeRegistry · Dialect     │
├───────────────────────────────────────────────────────────────┤
│ L1  Protocol      RowBinary codec · parameter binding         │
├───────────────────────────────────────────────────────────────┤
│ L0  Transport     Apache HttpClient 5 pooled client (HTTP + RowBinary) │
└────────────────────────────────────────────────────────────────────────┘
```

Each layer depends only downward. L5 is entirely optional sugar — a user can call L4
directly, which is what makes the library usable outside Spring.

## 5. Core Component Responsibilities

### 5.1 `EntityMetadataResolver` (L2)
Resolves a Java class into an immutable `EntityMetadata` **once, at startup**.
Produces: table identity, engine descriptor, ordered column list, per-column
`TypeHandler` binding, and pre-compiled accessors (`MethodHandle` getters/setters, or
canonical-constructor invokers for records).

*Why it matters:* NFR-4 and NFR-2 both hinge on never doing `Field.get()` in a row loop.

### 5.2 `TypeRegistry` + `TypeHandler<J,C>` (L2)
The extension point that keeps the library alive as ClickHouse evolves (R-2).
A handler knows how to (a) declare its ClickHouse type string, (b) write a Java value into
a `RowBinary` stream, (c) read one back. Composite handlers (`Array`, `Map`, `Tuple`,
`Nullable`, `LowCardinality`) wrap inner handlers recursively, mirroring ClickHouse's own
type-tree structure.

Resolution order: explicit `@ChColumn(type=…)` → user-registered handler → built-in handler
→ startup failure with an actionable message (FR-1.10).

### 5.3 `ChTemplate` (L4) — the keystone
The single facade through which every path executes. Responsibilities:
bind parameters → acquire connection → execute → translate exceptions → map rows →
emit metrics/traces → release resources.

Both the DSL and native SQL funnel through it, which is how P4 (escape-hatch parity) is
achieved structurally rather than by discipline.

### 5.4 `BatchWriter<T>` (L5/L4)
A bounded, self-flushing ingest buffer. Owns: bounded queue, flush scheduler, background
flusher pool, retry engine, backpressure policy, dead-letter dispatch, drain-on-shutdown.

*Design stance:* this is the **primary** write API. `insert(T)` exists, is instrumented as
an anti-pattern, and is documented as such (P2, R-1).

### 5.5 `SchemaManager` (L3)
`generate → introspect → diff → decide`. The `decide` step is a guarded state machine, not
a boolean. Destructive changes require two independent opt-ins (FR-6.4) because the
failure mode is unrecoverable data loss.

### 5.6 Spring Data adapter (L5)
Implements Spring Data's `RepositoryFactorySupport` SPI. Translates `PartTree` method names
into the same internal `QueryModel` the DSL produces — so derived methods, `@Query`, and the
DSL all converge on one renderer and one mapper.

## 6. Key Data Flows

### 6.1 Read — derived repository method

```
findByTenantIdAndTsBetween(42, from, to, cursor)
      │
      ▼ (proxy intercept)
PartTree parse ──► QueryModel ──► SelectStatement AST
                                       │
                                       ▼
                            SqlRenderer + ParameterBinder
                                       │
                                       ▼
                            ChTemplate.query(...)
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
             ConnectionProvider   Metrics/Trace     Exception xlate
                    │
                    ▼
             ClickHouse ──► RowBinary stream ──► RowMapper ──► Slice<PageView>
```

Result sets are consumed as a **stream**, not materialised (NFR-3). The `Slice` implementation
pulls `pageSize + 1` rows to determine `hasNext` without a `COUNT(*)`.

### 6.2 Write — buffered ingest

```
writer.add(event)
   │
   ▼
Bounded queue ──(full?)──► Backpressure policy: BLOCK | DROP_OLDEST | FAIL
   │
   ▼ (flush trigger: rowCount ≥ N  ∨  bytes ≥ B  ∨  elapsed ≥ T)
Flusher thread
   │
   ▼
RowBinaryWriter: List<T> ──► columnar block (via TypeHandlers)
   │
   ▼
INSERT INTO t FORMAT RowBinary  ──► ClickHouse
   │                                    │
   ├── success ──► metrics                └── error
   │                                          │
   │                          ┌───────────────┴───────────────┐
   │                          ▼                               ▼
   │                    retryable?                        terminal
   │                    backoff+jitter, retry      BatchFailureHandler
   │                          │                    (dead letter)
   └──────────────────────────┘
```

**No silent drop path exists.** Every row terminates in either "acked by ClickHouse" or
"handed to the failure handler" (NFR-7).

### 6.3 Startup — schema lifecycle

```
Spring context refresh
   │
   ▼
Scan @ChTable entities ──► EntityMetadataResolver ──► metadata cache
   │
   ▼
SchemaMode?
   ├─ NONE              → done
   ├─ VALIDATE          → introspect + diff → any drift? fail fast with report
   ├─ CREATE_IF_MISSING → create absent tables only
   └─ UPDATE            → apply non-destructive changes;
                          destructive → refuse unless allowDestructive=true
```

## 7. Key Design Decisions (ADR summaries)

| ADR | Decision | Alternatives rejected | Rationale |
|-----|----------|----------------------|-----------|
| **ADR-01** | Build a ClickHouse-native API, not a Hibernate dialect. | Hibernate `Dialect` implementation. | Hibernate's contract assumes transactions, row identity, and `UPDATE`/`DELETE` semantics ClickHouse lacks. Every workaround is a lie (P1). |
| **ADR-02** | `clickorm-core` is Spring-free. | Build directly on Spring Data. | Widens the addressable audience (Quarkus/Micronaut/plain Java), forces clean boundaries, and insulates against Spring Data SPI churn (R-5). |
| **ADR-03** | `RowBinary` block serialisation for writes. | `PreparedStatement.addBatch()`. | JDBC batch is row-oriented and allocation-heavy; NFR-1 is unreachable through it. |
| **ADR-04** | Pluggable transport SPI, native client default. | Hard-wire JDBC. | JDBC is needed for tooling/BI compatibility; native is needed for throughput. Neither can be the only option. Decision confirmed by the Phase-0 spike. |
| **ADR-05** | Metamodel via annotation processor, **optional**. | Runtime proxy-based typed API; mandatory processor. | Compile-time safety without making the processor a hard dependency (R-6). String API always works. |
| **ADR-06** | No transaction manager. | A no-op `PlatformTransactionManager`. | A no-op that silently accepts `@Transactional` is worse than an absence, because it teaches a false model. Documented loudly instead. |
| **ADR-07** | Keyset pagination primary; offset supported with warnings. | Offset-only (JPA-familiar). | Deep offset scans are pathological on a columnar store. Familiarity is not worth the production incident (R-1). |
| **ADR-08** | Dictionaries as the answer to "relations". | `@ManyToOne` emulation via joins. | `dictGet*` is the ClickHouse-native, performant pattern. Emulating relations invites N+1 (P6). |
| **ADR-09** | Errors are typed by ClickHouse error code. | Generic `SQLException` passthrough. | Retry classification (FR-5.5) and actionable diagnostics both require semantic error types. |
| **ADR-10** | Schema `UPDATE` mode requires two opt-ins for destructive changes. | Single `ddl-auto`-style property. | JPA's `ddl-auto=update` has caused real data loss for a decade. Do not repeat the ergonomics of a known footgun. |

## 8. Concurrency Model

| Component | Model |
|-----------|-------|
| `ChTemplate` | Stateless, thread-safe, shared singleton. |
| Metadata cache | Immutable after startup; lock-free reads. |
| `TypeRegistry` | Copy-on-write; mutation only during initialisation. |
| Connection pool | Apache HttpClient 5 `PoolingHttpClientConnectionManager`. One pool per `ChTemplate` (shared with all `BatchWriter` children). Background evictor removes idle/half-open connections. `ChTemplate` implements `Closeable`; Spring releases the pool on context shutdown. |
| `BatchWriter` | MPSC bounded queue (`ArrayBlockingQueue`, many producers, dedicated flusher pool). Flusher count configurable; ordering guaranteed *within* a batch, not across batches. |
| Streaming reads | Cursor bound to a borrowed connection; must be closed. Leak detector logs unclosed streams. |

**Explicit non-guarantee:** ClickORM makes no global ordering guarantee across concurrent
batches. Documented, because the alternative (a single-threaded writer) would fail NFR-1.

## 9. Error Handling Strategy

```
ClickHouse error code / transport failure
              │
              ▼
   ChExceptionTranslator (core)
              │
   ┌──────────┴───────────┐
   ▼                      ▼
ChException tree     retryability classification
(ChSyntax, ChTimeout,   (RETRYABLE | TERMINAL | UNKNOWN)
 ChMemoryLimit,               │
 ChTooManyParts,              ▼
 ChConnection, …)      retry engine / dead letter
              │
              ▼
   SpringExceptionTranslator (spring module)
              │
              ▼
   DataAccessException hierarchy (for Spring users)
```

`UNKNOWN` is treated as terminal by default — retrying an unclassified error risks
duplicating data. Users can override the classifier.

## 10. Configuration Surface (representative)

```yaml
clickorm:
  url: http://clickhouse:8123
  database: analytics
  username: app
  password: ${CH_PASSWORD}
  transport: native            # native | jdbc | http
  compression: lz4
  pool:
    max-total: 200          # total connections across all routes (default: 200)
    max-per-route: 50       # connections per ClickHouse host (default: 50)
    connect-timeout: 5s     # TCP connect timeout (default: 5s)
    socket-timeout: 60s     # read/write timeout — set high for OPTIMIZE (default: 60s)
    idle-evict-after: 30s   # evict connections idle longer than this (default: 30s)
    evictor-interval: 5s    # background eviction sweep interval; 0 to disable (default: 5s)
    validate-after-inactivity: 10s  # re-validate before leasing stale conn (default: 10s)
  query:
    default-timeout: 30s
    settings:
      max_execution_time: 60
      max_memory_usage: 10000000000
    offset-warning-threshold: 10000
  batch:
    max-rows: 100000
    max-bytes: 32MB
    flush-interval: 1s
    backpressure: BLOCK        # BLOCK | DROP_OLDEST | FAIL
    async-insert: false
    retry:
      max-attempts: 5
      initial-backoff: 200ms
      max-backoff: 10s
    drain-timeout: 30s
  schema:
    mode: NONE                 # NONE | VALIDATE | CREATE_IF_MISSING | UPDATE
    allow-destructive: false   # second, independent opt-in (ADR-10)
    emit-scripts-to: ./migrations
  metrics:
    enabled: true
  tracing:
    enabled: true
```

Defaults are chosen for **production safety, not demo convenience**: `schema.mode: NONE`,
`allow-destructive: false`, `async-insert: false`.

## 11. Security Design

| Concern | Control |
|---------|---------|
| SQL injection | All values bound server-side. The AST renderer emits placeholders only; there is no code path that concatenates a user value into SQL. Identifiers are validated against a strict pattern and quoted. Enforced by a dedicated test suite + fuzzing. |
| Credentials | Read from config/secret manager, never logged, redacted in diagnostics endpoint. |
| Transport | TLS and mTLS supported; certificate validation on by default. |
| Log/trace leakage | SQL is tagged in parameterised form; parameter values are never attached to spans or logs (NFR-6). |
| Least privilege | Docs prescribe separate read/write ClickHouse roles; `UPDATE` schema mode requires DDL grants the runtime user should not normally hold. |
| Supply chain | Signed artifacts, SBOM, CVE scanning in CI, no GPL/AGPL transitives (NFR-13). |

## 12. Performance Design Principles

1. **Resolve once, execute many.** Metadata, type handlers, accessors, and rendered SQL
   templates are all computed at startup or first use and cached.
2. **Never reflect in a loop.** `MethodHandle`/`LambdaMetafactory` accessors only.
3. **Stream, don't materialise.** Reads are cursor-based; buffers are byte-bounded.
4. **Batch is the unit of work.** Writes are measured in blocks, never rows.
5. **Minimise copies.** Direct `RowBinary` serialisation from the entity into the output
   buffer, with no intermediate `Object[]`/`Map` representation.
6. **Measure continuously.** JMH gates in CI from Phase 0; a regression blocks the merge
   (NFR-1, NFR-2).

## 13. Deployment & Distribution

- Artifacts on Maven Central under `io.clickorm:*`, Apache 2.0.
- Semantic versioning; public API frozen at 1.0 (NFR-12).
- BOM (`clickorm-bom`) so users pin one version.
- Compatibility matrix published per release: ClickHouse server × Spring Boot × Java.

## 14. What This Architecture Deliberately Cannot Do

Stated explicitly, because these are design outputs rather than omissions:

- Cannot roll back a write. There are no transactions.
- Cannot track entity state or auto-detect changes. There is no persistence context.
- Cannot navigate object graphs lazily. There are no relations.
- Cannot guarantee cross-batch write ordering.
- Cannot make deep offset pagination fast.
- Cannot make ClickHouse behave like Postgres — and will not try (P1).

This list belongs in the user documentation verbatim. Setting expectations correctly is
the single highest-leverage thing this project can do to avoid R-1.
