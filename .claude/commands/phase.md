# ClickORM — Start or continue work on a build phase

$ARGUMENTS should be the phase number (0–8). If absent, ask.

## Phase context reference

### Phase 0 — Foundations & Spike (no release)
**Goal:** Prove riskiest technical assumptions.
**Build:**
- Repo scaffolding: multi-module Gradle/Maven, CI (GitHub Actions), Testcontainers, code style, license headers
- Spike A: transport bake-off — `clickhouse-jdbc` vs `client-v2` native vs HTTP + RowBinary (resolves Q-1)
- Spike B: type round-trip for the 12 hardest types: `UInt64`, `Int256`, `Decimal128`, `DateTime64(9,'Asia/Kolkata')`, `LowCardinality(Nullable(String))`, `Array(Array(String))`, `Map(String, Array(UInt32))`, `Tuple(String, UInt8)`, `Enum8`, `IPv6`, `UUID`, `JSON`
- Spike C: minimal `RepositoryFactoryBean` (resolves Q-5)
- JMH benchmark module wired into CI, NFR-1/NFR-2 baselines recorded

**Exit criteria:** Transport ADR with benchmark evidence · 12 hard types round-trip · CI < 5 min · ADR log established

---

### Phase 1 — Core Mapping & Read Path → v0.1.0
**Goal:** Define an entity, run a query, get typed objects back. Zero Spring.
**Build order (from LLD §15):** `type/` → `protocol/` → `connection/` → `metadata/` → `mapping/` → `template/` → `exception/`

Key implementation notes:
- **`type/` FIRST** — UUID byte order and DateTime64 timezone bugs corrupt data everywhere else
- All reflection resolved at startup via `LambdaMetafactory` / `MethodHandle` — no `Field.get()` in row loops
- `RowBinaryWithNamesAndTypes` for reads (header enables bind-by-name drift detection)
- Named parameters, server-side binding only (NFR-6 — security-critical)
- FRs: FR-1.1–1.4, FR-1.10, FR-2.1–2.8, FR-2.12, FR-3.10, FR-3.11, FR-3.12, FR-3.13

**Exit criteria:** Demo works (see plan §Phase 1) · NFR-2 met (≤15% overhead) · 85% coverage on core · all FR-2 Must types integration-tested

**Deliverable:** `clickorm-core` on Maven Central, zero Spring dependencies (NFR-9)

---

### Phase 2 — Write Path & Ingestion → v0.2.0
**Goal:** Ingest at production throughput without the user writing buffer code.
**Build order:** `RowBinaryWriter` → `BatchWriter` → `RetryPolicy` → `BatchFailureHandler`

Key implementation notes:
- `RowBinary` block serialisation only — NO per-row `PreparedStatement.addBatch()`
- Ring buffer: MPSC, bounded, byte-limited (not just row-limited)
- Flush triggers: row count ≥ N OR bytes ≥ B OR elapsed ≥ T (all three, any fires)
- Backpressure: `BLOCK` / `DROP_OLDEST` / `FAIL`
- Error classification: retryable codes (159, 202, 203, 209, 210, 241, 252, 999) vs terminal (47, 53, 60, 62, 81, 192) — see LLD §9.2
- On `MEMORY_LIMIT_EXCEEDED` (241) and `TOO_MANY_PARTS` (252): halve batch size on retry
- Single-row `insert(T)` increments `clickorm.insert.singlerow` counter + WARN log (P2)
- FRs: FR-5.1–5.7

**Exit criteria:** NFR-1 met (≥90% hand-tuned throughput) · Chaos test (container kill mid-ingest) · Graceful shutdown test (500k rows, SIGTERM)

---

### Phase 3 — Schema & DDL → v0.3.0
**Goal:** Schema as code, migrations a DBA would sign off on.

Key implementation notes:
- Engine descriptors with validation (e.g. `ReplacingMergeTree` must have a version column)
- DDL includes: `ENGINE`, `ORDER BY`, `PARTITION BY`, `PRIMARY KEY`, `SAMPLE BY`, `TTL`, codecs, `SETTINGS`, `ON CLUSTER`
- `SchemaMode` state machine: `NONE` → `VALIDATE` → `CREATE_IF_MISSING` → `UPDATE`
- `UPDATE` mode requires TWO independent opt-ins for destructive changes (`allowDestructive=true`) — ADR-10, not negotiable
- `EngineMismatch` and `OrderByMismatch` are never auto-fixable (require table rebuild)
- VALIDATE failure must print a human-readable table, not a stack trace
- FRs: FR-6.1–6.4

**Exit criteria:** Round-trip test (generate → create → introspect → diff → zero differences) for every engine · Destructive guard proven by test · Flyway interop example

---

### Phase 4 — Spring Boot Starter & Repositories → v0.4.0
**Goal:** The Spring developer experience. This is the adoption inflection point.

Key implementation notes:
- `PartTree` → `QueryModel` → same `SelectStatement` AST the DSL uses (one renderer, one mapper)
- `IgnoreCase` wraps in `lower()` — must warn (defeats index)
- Keyset pagination as primary; offset `Page<T>` supported with a WARN above the threshold
- `IsNull` on non-nullable columns and nested property paths: rejected at context refresh, not first call
- `SchemaManager` must run before any `BatchWriter` bean (`@DependsOn`)
- `@ClickHouseTest` slice boots in < 10s with a reused container
- FRs: FR-4.1–4.4, FR-7.1–7.2, FR-7.4, FR-7.5, FR-7.8, FR-7.10

**Exit criteria:** Zero-config demo works (just `application.yml` + entity + repository) · `@ClickHouseTest` < 10s

**Why this is the biggest phase:** Spring Data's repository SPI has genuine edge cases with a non-relational dialect. Budget contingency here.

---

### Phase 5 — Typed Query Builder & Metamodel → v0.5.0
**Goal:** Compile-time column safety and analytics-native query construction.

Key implementation notes:
- Annotation processor is OPTIONAL — `col("ts")` string API must always work (ADR-05, R-6)
- AST: `Expression` / `Predicate` / `SelectStatement` node model (see LLD §6)
- Identifier quoting: backticks, validated against `^[A-Za-z_][0-9A-Za-z_]*$` before quoting
- Literals can only come from internal constants — no `LiteralFromUserInput` type exists (NFR-6)
- ClickHouse-specific: `FINAL`, `SAMPLE`, `PREWHERE`, `LIMIT n BY`, `WITH TOTALS`, `WITH ROLLUP/CUBE`
- FRs: FR-3.1–3.9, FR-3.11–3.13

**Exit criteria:** 30 representative analytical queries expressible without raw SQL · Rename a field → build fails · SQL golden-file snapshot tests

---

### Phase 6 — Observability & Resilience → v0.6.0
**Goal:** Operable in production by people who didn't write it.

Key metrics (see LLD §13 for full table):
- `clickorm.query.duration` (Timer, tags: table/operation/repository/method/outcome)
- `clickorm.insert.singlerow` (anti-pattern counter)
- `clickorm.insert.dead_letter.rows`
- `clickorm.buffer.rows` / `.bytes` (Gauge)

`query_id` format: `clickorm-{appName}-{traceId|uuid}` — enables `system.query_log` correlation (NFR-10).
SQL in spans: parameterised form only — never attach parameter values (NFR-6).

**Exit criteria:** Grafana dashboard JSON in repo · Runbook page: "ClickORM is slow / dropping data"

---

### Phase 7 — Advanced ClickHouse Features → v0.9.0
**Goal:** Cover the features that make ClickHouse ClickHouse.
Deferred from Lean v1.0. Build only after Phase 4 has external adopters.
- Materialized views, dictionaries, distributed tables, `AggregateFunction` columns, JSON/Variant, `MutationOperations`, geo types

---

### Phase 8 — Hardening, Docs & GA → v1.0.0
**Goal:** Earn the 1.0 API-stability promise.
- API freeze (all internals in `…internal.*`), compat matrix (CH 24.3/24.8/latest × SB 3.2–3.4 × Java 17/21)
- Docs site: "Coming from JPA" guide + "What ClickORM deliberately does not do" page
- Benchmarks published, security review, GraalVM hints, Maven Central release engineering

**v1.0 success criteria (from requirements §9):**
- All Must FRs implemented + integration-tested
- NFR-1 and NFR-2 verified by public benchmark
- ≥ 3 external production adopters named
- Documentation site live
- Zero known data-loss defects

---

## Design principles to keep in mind during any phase

| Principle | Consequence |
|---|---|
| P1 No lies about semantics | No `@Transactional` illusion, no dirty-checking, no rollback |
| P2 Batch is the default write path | Single-row insert is an anti-pattern |
| P3 Analytics first-class | `FINAL`, `SAMPLE`, `PREWHERE`, window fns — not escape-hatched |
| P4 Escape hatch parity | Native SQL shares the same binding/mapping/metrics as the DSL |
| P5 Schema is code | DDL generated + diffed, never auto-applied without explicit opt-in |
| P6 No relational modelling | No `@OneToMany`, no cascades, no lazy proxies, no N+1 |

## Dependency graph (don't start a phase before its prerequisites)

```
Phase 0 → Phase 1 → Phase 2 → Phase 6
                  → Phase 3 → Phase 4 → Phase 7 → Phase 8
                  → Phase 5 (can run in parallel with 2/3)
```

## When presenting the work for a phase

1. State the goal and exit criteria.
2. Identify which source files/packages to create or modify (reference the module layout in HLD §3 and LLD §1).
3. Implement in the order specified for that phase.
4. After each component, confirm the exit criterion for that component is met before moving on.
