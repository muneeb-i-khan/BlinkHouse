# ADR-04 — ClickHouse Transport Selection

**Status:** Accepted  
**Date:** 2026-08-23  
**Deciders:** BlinkHouse core team  
**Spike:** `SpikeABenchmark` — `blinkhouse-benchmark/src/main/java/io/blinkhouse/benchmark/SpikeABenchmark.java`

---

## Context

BlinkHouse needs a transport layer between the framework and ClickHouse. Three candidates exist:

| Candidate | Description |
|---|---|
| **(a) `clickhouse-jdbc`** | JDBC driver maintained by ClickHouse Inc. Standard `PreparedStatement` batch API. |
| **(b) `client-v2`** | Official ClickHouse Java client v2. HTTP + Apache HttpClient 5 internally. Supports native TCP protocol (port 9000), connection pooling, compression, retry. |
| **(c) Raw HTTP + RowBinary** | `java.net.http.HttpClient` (JDK built-in) posting hand-serialised RowBinary bytes via `ChOutputStream`. |

The decision resolves **Q-1** from the requirements and directly governs **NFR-1** (bulk insert throughput ≥ 90% of a hand-tuned client benchmark).

---

## Benchmark evidence

**Environment:** MacBook, Docker Desktop, `clickhouse/clickhouse-server:24.8`, JDK 21.  
**Schema:** 5 columns (`UInt32`, `DateTime64(3)`, `UUID`, `LowCardinality(String)`, `UInt32`), `MergeTree`.  
**JMH settings:** 2 warmup × 10 s, 3 measurement × 20 s, fork 1, batch size 100 000 rows.  
**Note:** client-v2 was tested with default settings (untuned). Compression and connection strategy were not explicitly configured.

### Insert throughput

| Transport | ops/s | ms/batch (avg) | rows/sec |
|---|---|---|---|
| Raw HTTP + RowBinary | **29.4** | **32.6** | **~2 900 000** |
| client-v2 (default) | 6.0 | 172 | ~600 000 |
| JDBC batch | 4.3 | 230 | ~430 000 |

### Scan throughput (HTTP RowBinary)

| Transport | ops/s | ms/batch | rows/sec |
|---|---|---|---|
| Raw HTTP + RowBinary | 7.7 | 124 | ~770 000 |

### Key observation

Both `insertHttpRowBinary` and `insertClientV2` posted **identical pre-serialised RowBinary bytes** to the same container. The 5× gap is entirely transport-stack overhead. `javap` inspection confirms client-v2 uses **Apache HttpClient 5** (`org.apache.hc.client5`) with a full connection-pool lease/release lifecycle per request. `java.net.http.HttpClient` uses JDK HTTP/1.1 keep-alive with no pool machinery.

The client-v2 benchmark used default settings. Disabling compression and tuning `ConnectionReuseStrategy(LIFO)` may narrow the gap. This is not yet measured — it is a known caveat of this evidence.

---

## Decision

**Use a split transport model via the `ChConnectionProvider` SPI:**

### Write path — Raw HTTP + RowBinary

`BatchWriter` flushes use `java.net.http.HttpClient` posting RowBinary bytes serialised by `ChOutputStream`. Rationale:

- Throughput is existential for the write path. ~2.9M rows/sec vs ~600k rows/sec is the difference between BlinkHouse being useful for high-volume ingest and not.
- `BatchWriter` concurrency is bounded by `flusherThreads` (typically 2–8). `java.net.http.HttpClient` keep-alive handles this without a formal pool. A thin `ChConnectionPool` wrapper (Phase 1) adds bounded acquisition and leak detection for the cases where it matters.
- The write path is append-only and stateless — no cancellation, no progress streaming, no native protocol features are needed.

### Query / read path — client-v2

`ChTemplate` queries use client-v2 as the default `ChConnectionProvider` implementation. Rationale:

- Query workloads involve many concurrent short requests from different threads — exactly where Apache HttpClient 5's pool earns its overhead.
- Native TCP protocol (port 9000) gives progress streaming, mid-query cancellation, and server-push — features the HTTP API cannot provide.
- LZ4/ZSTD compression is negotiated automatically, reducing bandwidth on large result sets.
- Active maintenance by ClickHouse Inc. means new protocol features arrive without framework changes.
- The query path is latency-dominated, not serialisation-dominated. The transport overhead is amortised over network round-trips.

### JDBC — optional third transport

`clickhouse-jdbc` is retained as an optional `ChConnectionProvider` for tooling compatibility (Flyway, BI tools, JDBC-only environments). It is never the default for application code.

### NFR-1 baseline

Raw HTTP + RowBinary at **~2.9M rows/sec** is the hand-tuned benchmark. NFR-1 requires BlinkHouse `BatchWriter` to reach **≥ 2.6M rows/sec** (90% of baseline) on equivalent hardware and schema. This gate is enforced in CI via `blinkhouse-benchmark`.

---

## Alternatives rejected

### client-v2 for everything

Rejected because the untuned write throughput (~600k rows/sec) may not satisfy NFR-1. Even after tuning, the Apache HttpClient 5 pool lifecycle adds overhead that raw HTTP avoids by design. The risk is that tuning gets us to 80% of the raw path but we cannot close the remaining gap without forking the client.

Revisit if: the client-v2 tuned benchmark (compression off, LIFO strategy, keep-alive forced) reaches ≥ 2.6M rows/sec. At that point the complexity saving of a single transport outweighs the performance cost.

### Raw HTTP for everything (reads too)

Rejected because the query path needs connection pooling, and building a production-grade pool from scratch is equivalent in complexity to just using client-v2. The read path also benefits from native protocol features (cancellation, progress) that are impossible over HTTP.

### JDBC for everything

Rejected. ~430k rows/sec is well below NFR-1. Text encoding per row, per-row object creation, and `PreparedStatement` overhead are structural — not tunable away. Retained only as a compatibility escape hatch.

---

## Consequences

### Positive
- Write path is on the fastest measured transport with no open questions about NFR-1 feasibility.
- Read path gets native protocol, pooling, compression, and cancellation without building them.
- The SPI (`ChConnectionProvider`) keeps both transports swappable — a user can plug in Vert.x or OkHttp if their environment requires it.

### Negative / costs
- Two transport dependencies instead of one (`java.net.http` is JDK; `client-v2` adds ~5 MB transitively).
- `ChConnectionPool` must be built for the write path in Phase 1 — approximately 150 lines to implement bounded acquisition, configurable size, acquire timeout, and leak detection.
- If client-v2 changes its internal HTTP stack in a future version, the read-path performance characteristics may change without our involvement.

### Open
- **Client-v2 tuned benchmark not yet run.** If a follow-up shows tuned client-v2 reaches ≥ 2.6M rows/sec, collapse to a single transport. This would simplify Phase 1 significantly.
- **Native TCP protocol for writes.** Client-v2 supports native TCP. If write throughput over native TCP exceeds raw HTTP RowBinary, revisit this ADR at Phase 7 when distributed/replicated table support is added.

---

## References

- `blinkhouse-benchmark/src/main/java/io/blinkhouse/benchmark/SpikeABenchmark.java` — benchmark source
- `spike-a-results.json` — raw JMH output (root of repo after benchmark run)
- `03-HLD.md §7` — ADR-03 (RowBinary block serialisation for writes)
- `01-REQUIREMENTS.md` — NFR-1, NFR-2, Q-1
