# ClickORM — Scaffold a new @ChTable entity

The user wants to create a new ClickORM entity. Follow this process exactly.

## Step 1 — Gather intent

Ask (if not already given in $ARGUMENTS):
- **Java class name** (e.g. `PageView`)
- **Table name** (snake_case, or derive from class name via SnakeCase strategy)
- **Engine** — which MergeTree variant? (`MergeTree`, `ReplacingMergeTree`, `SummingMergeTree`, `AggregatingMergeTree`, `CollapsingMergeTree`, `VersionedCollapsingMergeTree`, or `Distributed`). Default: `MergeTree`.
- **Columns** — name, Java type, and any special ClickHouse type override
- **ORDER BY** — REQUIRED for any MergeTree family. Reject the entity at startup without it.
- **PARTITION BY** — optional but strongly recommended for time-series data

## Step 2 — Apply the canonical type mapping

Always consult this table before choosing a Java type or `@ChColumn(type=...)`:

| ClickHouse type | Preferred Java | Notes |
|---|---|---|
| `UInt8/16/32/64` | `short/int/long/long` | UInt64 overflows `long` above 2^63 — use `BigInteger` if needed |
| `Int8/16/32/64` | `byte/short/int/long` | |
| `Int128/256`, `UInt128/256` | `BigInteger` | |
| `Float32/64` | `float/double` | |
| `Decimal(P,S)` | `BigDecimal` | |
| `String` | `String` | |
| `LowCardinality(String)` | `String` | Use for low-cardinality strings (e.g. country, status, event_type) |
| `Date` / `Date32` | `LocalDate` | |
| `DateTime` | `Instant` | second precision |
| `DateTime64(P, 'TZ')` | `Instant` | Always specify timezone in the type string — NEVER use JVM default |
| `UUID` | `java.util.UUID` | Handler swaps byte halves vs RFC 4122 — this is automatic, but don't hand-roll UUID serialisation |
| `IPv4` / `IPv6` | `Inet4Address` / `Inet6Address` | |
| `Bool` | `boolean` | |
| `Enum8/16` | Java `enum` | Add `@ChEnumerated` |
| `Array(T)` | `List<T>` | |
| `Map(K,V)` | `Map<K,V>` | |
| `Nullable(T)` | Boxed type or `Optional<T>` | Primitives rejected at startup |

**Three silent-corruption traps — always verify:**
1. `UUID`: the `UuidHandler` swaps the two 64-bit halves. Never bypass it.
2. `DateTime64`: timezone is part of the column type, not the session. Write it as `"DateTime64(3, 'UTC')"`.
3. `FixedString(N)`: null padding stripped on read — don't store length-dependent binary data.

## Step 3 — Engine-specific validation rules

Apply these before generating code:

- `ReplacingMergeTree`: must have a `versionColumn` — a UInt64 or DateTime column.
- `SummingMergeTree`: summing columns must be numeric.
- `CollapsingMergeTree` / `VersionedCollapsingMergeTree`: must have a `signColumn` (Int8).
- `AggregatingMergeTree`: columns meant for pre-aggregation should use `AggregateFunction(fn, T)` or `SimpleAggregateFunction(fn, T)` types.
- `Distributed`: must specify `cluster`, `localTable`, and `shardingKey`.
- ALL MergeTree variants: `orderBy` is **non-negotiable** — the entity is rejected at startup without it.

## Step 4 — Codec recommendations

Suggest per-column codecs from this guide:
- Monotonically increasing integer / timestamp → `Delta` or `DoubleDelta` then `ZSTD(3)`
- Floating point sequences → `Gorilla` then `ZSTD(3)`
- High-entropy strings (UUIDs, hashes) → `ZSTD(3)` alone
- Low-entropy / repetitive strings → `LowCardinality` first, then possibly `ZSTD(3)`
- General-purpose → `ZSTD(3)` (level 1–3 is the practical range)

## Step 5 — Generate the entity

Produce a Java `record` (preferred) or POJO. Use this as a model:

```java
@ChTable(
    name = "table_name",
    orderBy = {"col1", "toDate(ts)"},   // ← REQUIRED
    partitionBy = "toYYYYMM(ts)",
    ttl = "ts + INTERVAL 90 DAY DELETE"
)
@ChEngine(value = Engine.REPLACING_MERGE_TREE, versionColumn = "version", replicated = false)
@ChSkipIndex(name = "idx_url", expression = "url", type = IndexType.TOKENBF_V1, granularity = 4)
public record MyEntity(
    @ChColumn(type = "UInt32")               int      tenantId,
    @ChColumn(type = "DateTime64(3, 'UTC')") Instant  ts,
                                             UUID     id,
    @ChColumn(type = "LowCardinality(String)") String category,
    @ChCodec({"Delta", "ZSTD(3)"})           long     counter,
    @ChColumn(nullable = true)               Long     optionalMs   // Nullable(UInt64) inferred
) {}
```

## Step 6 — Remind about what NOT to do

After generating the entity, add a short comment block reminding the user:
- ClickORM has no `@Transactional` support (no-op at best, misleading at worst — ADR-06).
- Single-row `insert(entity)` is an anti-pattern (P2) — use `BatchWriter` or `insertAll(...)`.
- There are no `@OneToMany` / `@ManyToMany` / lazy relations (P6) — use dictionaries or explicit joins.
- `ORDER BY` fields control MergeTree merge behaviour and query performance — choose carefully.
