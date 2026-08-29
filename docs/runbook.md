# BlinkHouse Runbook — "ClickHouse is slow / dropping data"

## Quick orientation

All metrics are emitted under the `blinkhouse.*` namespace. Import `docs/grafana-dashboard.json` into Grafana to get panels for every section below. Query IDs in `system.query_log` are always prefixed `blinkhouse-<appName>-` — use them to correlate application traces with server-side execution.

---

## Symptom 1: Queries are slow

### Step 1 — Check the latency percentiles panel

```
histogram_quantile(0.99, sum(rate(blinkhouse_query_duration_seconds_bucket[5m])) by (le, table, operation))
```

If p99 is elevated, check:

| Where to look | What it means |
|---|---|
| `operation=select`, p99 spikes | ClickHouse is slow serving reads — check `system.query_log` |
| `operation=insert`, p99 spikes | Serialisation or HTTP POST is slow — check `blinkhouse.batch.duration` |
| p99 normal but error rate high | Timeouts being swallowed — check `blinkhouse.query.duration{outcome="error"}` |

### Step 2 — Correlate with ClickHouse server

```sql
SELECT query_id, query, read_rows, memory_usage, elapsed
FROM system.query_log
WHERE query_id LIKE 'blinkhouse-<appName>-%'
  AND event_time > now() - INTERVAL 10 MINUTE
ORDER BY elapsed DESC
LIMIT 20;
```

### Step 3 — Common fixes

- **Missing index**: Check `EXPLAIN` on the slow query; add `ORDER BY` or `PREWHERE` columns.
- **Marks read too high**: Add a `PREWHERE` clause via the DSL (`ChQuery.prewhere(…)`) to exploit primary-key pre-filtering.
- **FINAL deduplication cost**: `FINAL` forces a merge scan. For high-write tables, consider scheduling offline merges instead.
- **Large result set**: Add `LIMIT` or `LIMIT n BY` to the `ChQuery`.

---

## Symptom 2: Data is being dropped

### Step 1 — Check the dead-letter counter

```
sum(increase(blinkhouse_insert_dead_letter_rows_total[1h])) by (table)
```

Any non-zero value means rows were written to the dead-letter handler after all retries were exhausted.

### Step 2 — Check application logs

Dead-lettered rows always produce an `ERROR` log line:

```
Dead-lettering N rows for <table> after M attempts: <exception message>
```

### Step 3 — Identify the root cause

| Error code | Meaning | Fix |
|---|---|---|
| 159 | `TIMEOUT_EXCEEDED` | Increase `clickhouse.query.default-timeout` or reduce batch size |
| 241 | `MEMORY_LIMIT_EXCEEDED` | Reduce `clickhouse.batch.max-rows` or `max-bytes`; BlinkHouse halves batch size on retry |
| 252 | `TOO_MANY_PARTS` | Merge is falling behind — reduce insert frequency or increase `parts_to_delay_insert` |
| 202/203 | Server overloaded | Back-pressure is correct; check server CPU/disk metrics |
| 47 (terminal) | `UNKNOWN_TABLE` | Schema not created; check `clickhouse.schema.mode` setting |
| 62 (terminal) | `SYNTAX_ERROR` | DDL/query bug; check application logs for the offending SQL |

### Step 4 — Recover dead-lettered rows

If `BatchFailureHandler` is configured, rows are passed to it for re-queuing or DLQ persistence. If not, they are logged only and lost. Configure a handler:

```java
BatchWriterConfig.builder()
    .failureHandler((batch, ex, attempts) -> myDlq.publish(batch))
    .build();
```

---

## Symptom 3: Buffer filling up

### Step 1 — Check buffer gauges

```
blinkhouse_buffer_rows{table="<table>"}
blinkhouse_buffer_bytes{table="<table>"}
```

The buffer fills when the flusher can't keep up with the producer.

### Step 2 — Diagnose

| Signal | Meaning |
|---|---|
| `batch.duration` p99 increasing | ClickHouse write latency rising |
| `batch.rows` rate dropping | Flusher threads are blocked or erroring |
| `query.duration{operation="insert"}` errors | Network or ClickHouse errors on every flush |

### Step 3 — Immediate relief

- Increase `clickhouse.batch.flusher-threads` (default 1) to add parallel flushers.
- Reduce `clickhouse.batch.max-rows` so each flush is smaller and faster.
- Switch to `async_insert=true` (`clickhouse.batch.async-insert=true`) to offload merge to ClickHouse.

### Step 4 — Backpressure policy

If the buffer fills completely, behaviour is controlled by `clickhouse.batch.backpressure-policy`:

| Policy | Behaviour |
|---|---|
| `BLOCK` (default) | Producer threads block until space is available |
| `DROP_OLDEST` | Oldest buffered rows are silently dropped |
| `FAIL` | `ChBufferFullException` is thrown to the producer |

For critical data, use `BLOCK`. For telemetry where loss is acceptable, use `DROP_OLDEST`.

---

## Symptom 4: Single-row insert anti-pattern

```
sum(rate(blinkhouse_insert_singlerow_total[5m])) by (table)
```

Any non-zero rate here means `ChTemplate.insertSingleRow()` is being called in a hot path. Each single-row insert creates a ClickHouse part, which degrades query performance over time.

**Fix**: Switch to `ChTemplate.batchWriter(EntityClass.class)` and call `batchWriter.add(entity)`. The `BatchWriter` handles buffering, flushing, and backpressure automatically.

---

## Alert rules (Prometheus / Alertmanager)

```yaml
groups:
  - name: blinkhouse
    rules:
      - alert: BlinkhouseDeadLetterNonZero
        expr: increase(blinkhouse_insert_dead_letter_rows_total[5m]) > 0
        for: 0m
        labels:
          severity: critical
        annotations:
          summary: "BlinkHouse is dropping rows to dead-letter for table {{ $labels.table }}"
          runbook: "See docs/runbook.md — Symptom 2"

      - alert: BlinkhouseQueryErrorRateHigh
        expr: |
          sum(rate(blinkhouse_query_duration_seconds_count{outcome="error"}[5m]))
          /
          sum(rate(blinkhouse_query_duration_seconds_count[5m])) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "BlinkHouse query error rate > 5%"
          runbook: "See docs/runbook.md — Symptom 1"

      - alert: BlinkhouseBufferFilling
        expr: blinkhouse_buffer_rows > 80000
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "BlinkHouse buffer for {{ $labels.table }} is over 80k rows"
          runbook: "See docs/runbook.md — Symptom 3"

      - alert: BlinkhouseSingleRowInsertHigh
        expr: rate(blinkhouse_insert_singlerow_total[5m]) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Single-row inserts detected on {{ $labels.table }} — use BatchWriter"
          runbook: "See docs/runbook.md — Symptom 4"
```

---

## Useful ClickHouse server queries

```sql
-- Active queries from this application
SELECT query_id, query, elapsed, read_rows, memory_usage
FROM system.processes
WHERE query_id LIKE 'blinkhouse-%'
ORDER BY elapsed DESC;

-- Recent errors
SELECT event_time, query_id, exception_code, exception
FROM system.query_log
WHERE query_id LIKE 'blinkhouse-%'
  AND type = 'ExceptionWhileProcessing'
  AND event_time > now() - INTERVAL 1 HOUR
ORDER BY event_time DESC
LIMIT 50;

-- Part count per table (should stay below ~3000 per partition)
SELECT table, count() AS parts, sum(rows) AS total_rows
FROM system.parts
WHERE active AND database = currentDatabase()
GROUP BY table
ORDER BY parts DESC;

-- Merge backlog
SELECT table, count() AS pending_merges
FROM system.merges
GROUP BY table;
```
