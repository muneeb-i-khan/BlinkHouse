# ClickORM — Scaffold a ClickHouseRepository interface

The user wants to create or extend a `ClickHouseRepository`. Follow this process.

## Step 1 — Identify the entity

Read the existing entity class (if any) from $ARGUMENTS or ask for it. Confirm:
- Entity class name and `@ChTable` config (especially `orderBy` columns — keyset pagination must use them)
- The "ID" type for `ClickHouseRepository<T, ID>` (ClickHouse has no row identity — the ID is for lookup queries only, not JPA-style identity tracking)

## Step 2 — Generate the interface skeleton

```java
public interface MyEntityRepository extends ClickHouseRepository<MyEntity, UUID> {
    // derived methods, @Query, and custom fragments go here
}
```

No `@Repository` annotation is needed — the starter auto-detects it.

## Step 3 — Derived query methods

Supported keywords → what they produce:

| Keyword | Example | Notes |
|---|---|---|
| `And` / `Or` | `findByTenantIdAndCountry` | Combines predicates |
| `Between` | `findByTsBetween` | Inclusive range |
| `LessThan`, `GreaterThan`, `…OrEqual` | `findByCountGt` | |
| `In` / `NotIn` | `findByCountryIn` | Bound as `Array` param server-side |
| `Like`, `StartingWith`, `Containing` | `findByUrlLike` | Pattern built server-side — WARN: defeats indexes |
| `IsNull` / `IsNotNull` | `findByDurationMsIsNull` | Rejected at startup if column is not `Nullable` |
| `True` / `False` | `findByActiveTrue` | |
| `OrderBy…Asc/Desc` | `findByTenantIdOrderByTsDesc` | |
| `Top` / `First` | `findTop10ByTenantId` | Adds LIMIT |
| `Distinct` | `findDistinctByCountry` | |

**`IgnoreCase` warns** — it wraps the column in `lower()`, which defeats any index on that column. Use only when correctness requires it.

**Invalid at startup (not runtime):** property paths that don't exist on the entity, `IsNull` on non-nullable columns, nested property traversal (no relations in ClickORM).

## Step 4 — Native SQL with @Query

Use `@Query` for anything analytical:

```java
@Query("""
    SELECT toStartOfHour(ts) AS hour,
           uniq(user_id) AS uniques,
           quantile(0.95)(duration_ms) AS p95
    FROM page_views
    WHERE tenant_id = :tenantId
      AND ts BETWEEN :from AND :to
    GROUP BY hour
    ORDER BY hour
    """)
List<HourlyStats> hourlyStats(@Param("tenantId") int tenantId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);
```

Rules for `@Query`:
- Named parameters only (`:name` syntax) — positional `?` is not supported.
- Parameters are ALWAYS bound server-side. Never interpolate user values into the SQL string.
- Project into a record or DTO by matching column aliases to record components positionally.
- `Stream<T>` return type triggers streaming mode (cursor-based, O(1) memory, must be closed).

## Step 5 — Pagination — always prefer keyset

**Keyset (correct):**
```java
Slice<PageView> findByTenantIdAndTsBetween(int tenantId, Instant from, Instant to, Cursor cursor);
// Cursor = Cursor.first(100, Sort.by("ts").descending())
// Next page = cursor.next(Map.of("ts", lastRow.ts(), "user_id", lastRow.userId()))
```

**Offset (`Pageable`) — discouraged:**
```java
// This works but logs a WARN above clickorm.query.offset-warning-threshold (default 10,000)
// and increments the clickorm.query.deep_offset counter
Page<PageView> findByTenantId(int tenantId, Pageable pageable);
```

Deep offset scans are pathological on a columnar store. If the user reaches for `Pageable`, suggest keyset and explain why.

## Step 6 — Async and streaming

```java
// Streaming — caller MUST close the stream (try-with-resources)
Stream<PageView> streamByTenantId(int tenantId);

// Async — CompletableFuture return type (deferred to post-1.0, note this if version < 1.0)
CompletableFuture<List<PageView>> findByTenantIdAsync(int tenantId);
```

## Step 7 — Remind about what NOT to do

After generating the repository, note:
- `@Transactional` on repository methods is silently meaningless — ClickHouse has no transactions (ADR-06). Don't add it.
- Avoid calling `insert(entity)` in a loop — use `insertAll(...)` or a `BatchWriter<T>`.
- There are no cascade operations. Saving one entity never touches another table.
- No `findById(id)` in a loop (N+1 pattern) — write a `findAllByIdIn(Collection<ID>)` instead.
