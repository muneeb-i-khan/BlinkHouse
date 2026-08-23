# ClickORM — Anti-pattern audit

Scan the code in $ARGUMENTS (or the current diff/file if not specified) for ClickHouse and ClickORM anti-patterns. For each finding, state: what it is, why it is dangerous, and the correct alternative.

## Anti-patterns to detect

### AP-1 — Single-row insert in a loop (P2 violation)

**Pattern:**
```java
for (Event e : events) {
    repository.insert(e);         // or template.insert(Event.class, List.of(e))
}
```

**Why dangerous:** Each insert is a separate HTTP round-trip. ClickHouse is a columnar store — it prefers large blocks. Single-row inserts cause `TOO_MANY_PARTS` errors under any real load and are instrumented as `clickorm.insert.singlerow` to make them visible in metrics.

**Fix:** Use `BatchWriter<T>` or `repository.insertAll(events)` / `template.insert(Event.class, events)`.

---

### AP-2 — Deep offset pagination (R-1)

**Pattern:**
```java
repository.findByTenantId(tenantId, PageRequest.of(page, size));  // page > ~1000
// or: OFFSET 500000 LIMIT 100 in a native query
```

**Why dangerous:** ClickHouse must scan and discard all rows up to the offset on every page. At `OFFSET 500,000` on a billion-row table this reads 500,000 rows per page fetch. Latency degrades linearly.

**Fix:** Keyset (cursor) pagination:
```java
Slice<PageView> findByTenantId(int tenantId, Cursor cursor);
// Cursor.first(100, Sort.by("ts").descending())
// cursor.next(Map.of("ts", lastRow.ts(), "user_id", lastRow.userId()))
```

---

### AP-3 — @Transactional on a ClickHouse repository or service (ADR-06 / P1 violation)

**Pattern:**
```java
@Transactional   // ← this
public void processEvents(List<Event> events) {
    repository.insertAll(events);
}
```

**Why dangerous:** ClickHouse has no ACID transactions. A `@Transactional` wrapper is silently meaningless (no-op if ClickORM's transaction manager is absent, or an outright lie if a JPA transaction manager is present and the code expects rollback). Users build production systems on top of a false model.

**Fix:** Remove `@Transactional`. If rollback behaviour is needed, use `insert_deduplication_token` for idempotency, and handle failures via `BatchFailureHandler`.

---

### AP-4 — N+1 pattern (P6 violation)

**Pattern:**
```java
List<Order> orders = orderRepo.findByTenantId(tenantId);
for (Order o : orders) {
    User user = userRepo.findById(o.getUserId());  // ← query per row
    ...
}
```

**Why dangerous:** Each loop iteration fires a separate ClickHouse query. With 10,000 orders this is 10,001 queries. ClickHouse is not optimised for point lookups.

**Fix:**
- Collect all IDs then batch-fetch: `userRepo.findAllByIdIn(userIds)`
- Or use a ClickHouse dictionary (`dictGet*`) for dimension lookups — it's the ClickHouse-native answer to `@ManyToOne`
- Or restructure the query with an explicit JOIN

---

### AP-5 — String interpolation / concatenation into SQL (NFR-6 / security-critical)

**Pattern:**
```java
String sql = "SELECT * FROM events WHERE country = '" + country + "'";   // SQL injection
String sql = "SELECT * FROM events WHERE tenant_id = " + tenantId;       // also bad
```

**Why dangerous:** SQL injection. A `country` value of `' OR '1'='1` corrupts the query. Even with "safe" types like integers, string construction bypasses ClickORM's server-side binding and breaks the security guarantee.

**Fix:** Always use named parameters:
```java
template.query("SELECT * FROM events WHERE country = :country AND tenant_id = :tenantId",
               Map.of("country", country, "tenantId", tenantId), mapper);
```

---

### AP-6 — Thread.sleep in retry logic (instead of `RetryPolicy`)

**Pattern:**
```java
while (attempts < 5) {
    try { repository.insertAll(rows); break; }
    catch (Exception e) { Thread.sleep(1000); attempts++; }
}
```

**Why dangerous:** Blocks a thread, swallows error classification (retryable vs. terminal), and applies a flat delay instead of exponential backoff with jitter. Under load, N threads sleeping 1s each = head-of-line blocking.

**Fix:** Configure `RetryPolicy` on `BatchWriterConfig` and let `BatchWriter` handle retries internally. For the read path, surface the exception and let the caller decide (or use a circuit breaker).

---

### AP-7 — Ignoring IgnoreCase on indexed columns

**Pattern:**
```java
List<PageView> findByCountryIgnoreCase(String country);
```

**Why dangerous:** `IgnoreCase` wraps the column in `lower(country)`, which prevents the ClickHouse primary index and any skip indexes on `country` from being used. Full table scan.

**Fix:** Normalise case at write time (store `country` as uppercase or lowercase), so no case-folding is needed at query time.

---

### AP-8 — Using UPDATE schema mode in production config

**Pattern:**
```yaml
clickorm:
  schema:
    mode: UPDATE
```

**Why dangerous:** `UPDATE` mode applies `ALTER TABLE` on every startup. In production this risks accidental schema changes from a misconfigured deploy, and `DropColumn` with `allowDestructive: true` causes permanent data loss.

**Fix:** Production should use `NONE` (default) or `VALIDATE`. Run `CREATE_IF_MISSING` only in a controlled bootstrap step. Use `UPDATE` only in local dev, and never commit it to production config.

---

### AP-9 — Materialising a full result set that should be streamed (NFR-3)

**Pattern:**
```java
List<Event> allEvents = repository.findAll();  // 50M rows
allEvents.forEach(this::process);
```

**Why dangerous:** `List<T>` materialises the entire result set in heap. For large tables this causes GC pressure or OOM.

**Fix:**
```java
try (Stream<Event> stream = repository.streamAll()) {
    stream.forEach(this::process);
}
```
`Stream<T>` is cursor-backed and O(1) in memory. **The stream MUST be closed** (use try-with-resources). An unclosed stream leaks a connection and logs a WARN with the originating stack trace.

---

## How to report findings

For each anti-pattern found:
1. Quote the offending code (file path + line number if available)
2. Name the anti-pattern (AP-1 through AP-9)
3. Explain the specific risk in this context
4. Provide the corrected version
5. Note any related metric that will surface this in production (e.g. `clickorm.insert.singlerow`, `clickorm.query.deep_offset`)

If no anti-patterns are found, say so explicitly and confirm what was checked.
