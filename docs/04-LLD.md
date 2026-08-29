# ClickORM — Low-Level Design (LLD)

**Companion docs:** `01-REQUIREMENTS.md`, `02-PHASED-PLAN.md`, `03-HLD.md`
**Document version:** 1.0
**Audience:** Implementers. Signatures below are the contract to build against.

---

## 1. Package Layout

```
io.clickorm
├── core
│   ├── annotation      ChTable, ChColumn, ChEngine, ChSkipIndex, ChTtl, ChCodec,
│   │                   ChNested, ChProjection, ChSettings, ChIgnore, ChEnumerated
│   ├── metadata        EntityMetadata, ColumnMetadata, EngineMetadata,
│   │                   EntityMetadataResolver, NamingStrategy, PropertyAccessor
│   ├── type            ClickHouseType, TypeParser, TypeHandler, TypeRegistry,
│   │                   handler/{Primitive,Temporal,Array,Map,Tuple,Nullable,
│   │                            LowCardinality,Enum,Decimal,Uuid,Ip,Json}Handler
│   ├── connection      ChConnectionProvider, ChConnection, ChClientOptions,
│   │                   NativeConnectionProvider, JdbcConnectionProvider, ChConnectionPool
│   ├── protocol        RowBinaryWriter, RowBinaryReader, ChInputStream, ChOutputStream,
│   │                   ColumnBlock, LEB128
│   ├── query           ast/{SelectStatement,Expression,Predicate,JoinClause,OrderSpec,…},
│   │                   SqlRenderer, ParameterBinder, BoundStatement, ChQuery, Functions
│   ├── mapping         RowMapper, RowMappers, EntityRowMapper, RecordRowMapper,
│   │                   SingleColumnRowMapper, MapRowMapper, ProjectionRowMapper
│   ├── template        ChTemplate, ChOperations, ChStream, QueryContext
│   ├── write           BatchWriter, BatchWriterConfig, BackpressurePolicy,
│   │                   RetryPolicy, ErrorClassifier, BatchFailureHandler, FlushTrigger
│   ├── schema          DdlGenerator, SchemaIntrospector, SchemaDiff, SchemaChange,
│   │                   SchemaManager, SchemaMode, MigrationScriptWriter
│   ├── observability   ChMetrics, ChTracer, QueryIdGenerator
│   └── exception       ChException + subtypes, ChExceptionTranslator, ChErrorCode
├── processor           MetamodelProcessor, MetamodelWriter
├── spring
│   ├── repository      ClickHouseRepository, SimpleClickHouseRepository,
│   │                   ClickHouseRepositoryFactory(Bean), PartTreeQuery,
│   │                   NativeChQuery, ChQueryLookupStrategy, ChEntityInformation
│   ├── config          EnableClickHouseRepositories, ChRepositoryRegistrar
│   └── support         SpringChExceptionTranslator, ChDatasource
├── boot                ClickOrmAutoConfiguration, ClickOrmProperties,
│                       ClickOrmHealthIndicator, ClickOrmMetricsAutoConfiguration
└── test                ClickHouseTest, ChTestContainer, FixtureLoader, TableTruncator
```

**Naming convention:** internal classes live in `…​.internal.*` and are excluded from the
1.0 API freeze (HLD §7, ADR "API review").

---

## 2. Annotations

### 2.1 `@ChTable`

```java
@Retention(RUNTIME) @Target(TYPE)
public @interface ChTable {
    String name() default "";              // default: NamingStrategy applied to class name
    String database() default "";          // default: connection default database
    String[] orderBy() default {};         // REQUIRED for MergeTree family
    String[] partitionBy() default {};
    String[] primaryKey() default {};      // defaults to orderBy prefix
    String sampleBy() default "";
    String ttl() default "";
    String onCluster() default "";
    ChSetting[] settings() default {};
    String comment() default "";
}
```

### 2.2 `@ChEngine`

```java
@Retention(RUNTIME) @Target(TYPE)
public @interface ChEngine {
    Engine value() default Engine.MERGE_TREE;
    String versionColumn() default "";     // ReplacingMergeTree
    String isDeletedColumn() default "";   // ReplacingMergeTree (CH 23.2+)
    String[] summingColumns() default {};  // SummingMergeTree
    String signColumn() default "";        // Collapsing / VersionedCollapsing
    boolean replicated() default false;
    String zkPath()  default "/clickhouse/tables/{shard}/{database}/{table}";
    String replica() default "{replica}";
    // Distributed
    String cluster() default "";
    String localTable() default "";
    String shardingKey() default "";
}

public enum Engine {
    MERGE_TREE, REPLACING_MERGE_TREE, SUMMING_MERGE_TREE, AGGREGATING_MERGE_TREE,
    COLLAPSING_MERGE_TREE, VERSIONED_COLLAPSING_MERGE_TREE, GRAPHITE_MERGE_TREE,
    DISTRIBUTED, MEMORY, NULL, BUFFER, LOG, TINY_LOG, STRIPE_LOG, DICTIONARY, SET, JOIN
}
```

### 2.3 `@ChColumn`, `@ChCodec`, `@ChSkipIndex`

```java
@Retention(RUNTIME) @Target({FIELD, RECORD_COMPONENT})
public @interface ChColumn {
    String name() default "";
    String type() default "";              // explicit override, e.g. "LowCardinality(String)"
    boolean nullable() default false;
    String defaultExpression() default "";
    String materialized() default "";
    String alias() default "";
    boolean ephemeral() default false;
    String ttl() default "";
    String comment() default "";
    int order() default Integer.MAX_VALUE;  // physical column order
}

@Retention(RUNTIME) @Target({FIELD, RECORD_COMPONENT})
public @interface ChCodec { String[] value(); }   // {"Delta","ZSTD(3)"}

@Retention(RUNTIME) @Target(TYPE) @Repeatable(ChSkipIndexes.class)
public @interface ChSkipIndex {
    String name();
    String expression();
    IndexType type();
    int granularity() default 1;
    String[] params() default {};     // e.g. bloom_filter false-positive rate
}

public enum IndexType { MINMAX, SET, BLOOM_FILTER, NGRAMBF_V1, TOKENBF_V1 }
```

### 2.4 Example entity

```java
@ChTable(
    name = "page_views",
    orderBy = {"tenant_id", "toDate(ts)", "user_id"},
    partitionBy = "toYYYYMM(ts)",
    ttl = "ts + INTERVAL 90 DAY DELETE"
)
@ChEngine(value = Engine.REPLACING_MERGE_TREE, versionColumn = "ingested_at", replicated = true)
@ChSkipIndex(name = "idx_url", expression = "url", type = IndexType.TOKENBF_V1, granularity = 4)
public record PageView(
    @ChColumn(type = "UInt32")                      int      tenantId,
    @ChColumn(type = "DateTime64(3, 'UTC')")        Instant  ts,
                                                    UUID     userId,
    @ChColumn(type = "LowCardinality(String)")      String   country,
    @ChCodec({"ZSTD(3)"})                           String   url,
    @ChColumn(nullable = true)                      Integer  durationMs,
                                                    Map<String,String> tags,
                                                    List<String> experiments,
    @ChColumn(defaultExpression = "now64(3)")       Instant  ingestedAt
) {}
```

---

## 3. Metadata Layer

```java
public final class EntityMetadata<T> {
    Class<T> javaType();
    String database();
    String table();
    String qualifiedName();                    // `db`.`table`
    EngineMetadata engine();
    List<ColumnMetadata> columns();            // physical order, excludes ALIAS/EPHEMERAL
    List<ColumnMetadata> insertableColumns();  // excludes MATERIALIZED/ALIAS/EPHEMERAL
    Optional<ColumnMetadata> column(String name);
    List<SkipIndexMetadata> skipIndexes();
    List<String> orderBy(); List<String> partitionBy(); List<String> primaryKey();
    Optional<String> ttl(); Map<String,String> settings();
    InstanceFactory<T> instanceFactory();      // record ctor or no-arg + setters
}

public final class ColumnMetadata {
    String name();
    String javaName();
    Class<?> javaType();
    ClickHouseType chType();
    TypeHandler<?,?> handler();
    ValueAccessor accessor();                  // MethodHandle-backed, no reflection at runtime
    boolean nullable(); boolean materialized(); boolean alias(); boolean ephemeral();
    Optional<String> defaultExpression(); List<String> codecs(); Optional<String> ttl();
}
```

**`EntityMetadataResolver` algorithm**

1. Reject the class if `@ChTable` is absent → `ChMappingException` naming the class.
2. Resolve table/database via annotation → `NamingStrategy` fallback.
3. Enumerate record components or fields (skip `static`, `transient`, `@ChIgnore`).
4. For each: resolve name → resolve `ClickHouseType` (explicit override → registry lookup by
   Java type → composite resolution for generics via `TypeToken` → fail with a message naming
   the field, its Java type, and how to register a handler).
5. Build a `ValueAccessor` per column using `LambdaMetafactory` (falling back to
   `MethodHandle` for non-public members).
6. Validate: MergeTree family requires non-empty `orderBy`; `primaryKey` must be a prefix of
   `orderBy`; engine-specific columns must exist and be type-compatible.
7. Cache in a `ClassValue<EntityMetadata<?>>` — thread-safe, GC-friendly, no map contention.

**Failure mode is loud and specific.** Example message:

```
ClickORM: cannot map PageView.durationMs (java.lang.Integer)
  reason : field is annotated nullable=true but the resolved type is UInt32 (non-nullable)
  fix    : use @ChColumn(type = "Nullable(UInt32)") or make the field non-null
```

---

## 4. Type System

### 4.1 Type model

```java
public sealed interface ClickHouseType {
    String render();                       // "LowCardinality(Nullable(Decimal(18,4)))"
    boolean isNullable();

    record Primitive(String name)                      implements ClickHouseType {}
    record FixedString(int length)                     implements ClickHouseType {}
    record Decimal(int precision, int scale)           implements ClickHouseType {}
    record DateTime64(int precision, String timezone)  implements ClickHouseType {}
    record Enum8(Map<String,Byte> values)              implements ClickHouseType {}
    record Enum16(Map<String,Short> values)            implements ClickHouseType {}
    record Nullable(ClickHouseType inner)              implements ClickHouseType {}
    record LowCardinality(ClickHouseType inner)        implements ClickHouseType {}
    record Array(ClickHouseType element)               implements ClickHouseType {}
    record Map_(ClickHouseType key, ClickHouseType v)  implements ClickHouseType {}
    record Tuple(List<ClickHouseType> elements)        implements ClickHouseType {}
    record Nested(List<ColumnMetadata> columns)        implements ClickHouseType {}
    record AggregateFunction(String fn, List<ClickHouseType> args) implements ClickHouseType {}
    record Json()                                      implements ClickHouseType {}
}
```

`TypeParser.parse("Map(String, Array(Nullable(UInt32)))")` → recursive-descent parser
producing the tree above. Round-trip property test: `parse(t.render()).equals(t)` for a
generated corpus of ~500 type strings.

### 4.2 `TypeHandler` SPI

```java
public interface TypeHandler<J, C extends ClickHouseType> {
    C chType(TypeContext ctx);                       // declared type for DDL
    boolean supports(Class<?> javaType, ClickHouseType chType);
    void write(ChOutputStream out, J value, C type) throws IOException;
    J read(ChInputStream in, C type) throws IOException;
    default int priority() { return 0; }             // higher wins on conflict
}
```

Registration: `ServiceLoader` (core) **and** Spring beans of type `TypeHandler` (starter).
User handlers get `priority() = 100` by convention so they override built-ins.

### 4.3 Canonical type mapping (FR-2)

| ClickHouse | Java (preferred) | Also accepted | Notes |
|---|---|---|---|
| `Bool` | `boolean` | `Boolean` | |
| `Int8/16/32/64` | `byte/short/int/long` | boxed | |
| `Int128/256` | `BigInteger` | | |
| `UInt8` | `short` | `int`, `boolean` (0/1) | widened to avoid sign loss |
| `UInt16` | `int` | | |
| `UInt32` | `long` | | |
| `UInt64` | `long` | `BigInteger` | `long` overflows above 2^63 — configurable strictness |
| `UInt128/256` | `BigInteger` | | |
| `Float32/64` | `float/double` | boxed | |
| `Decimal(P,S)` | `BigDecimal` | | scale validated at write |
| `String` | `String` | `byte[]` | UTF-8 |
| `FixedString(N)` | `String` | `byte[]` | right-padded with `\0`; padding stripped on read |
| `LowCardinality(T)` | as `T` | | transparent to the user |
| `Date` | `LocalDate` | | |
| `Date32` | `LocalDate` | | extended range |
| `DateTime` | `Instant` | `LocalDateTime`, `ZonedDateTime` | second precision |
| `DateTime64(P,TZ)` | `Instant` | `OffsetDateTime` | TZ from type, not JVM default |
| `UUID` | `java.util.UUID` | | ClickHouse byte order differs — handler swaps halves |
| `IPv4` | `Inet4Address` | `String` | |
| `IPv6` | `Inet6Address` | `String` | |
| `Enum8/16` | Java `enum` | `String` | strategy via `@ChEnumerated` |
| `Array(T)` | `List<T>` | `T[]` | nesting supported |
| `Map(K,V)` | `Map<K,V>` | | insertion order not preserved |
| `Tuple(...)` | `record` | `Object[]`, `List<Object>` | record components matched positionally |
| `Nullable(T)` | boxed `T` | `Optional<T>` | primitives rejected at startup |
| `Nested(...)` | `List<R>` (record `R`) | | flattened to parallel arrays on the wire |
| `JSON` | `JsonNode` | `String`, POJO | Jackson; version-gated |
| `AggregateFunction(...)` | `byte[]` (opaque) | | read-through only; use `-Merge` in SQL |

**Three traps worth calling out to implementers:**
- `UUID` byte ordering in ClickHouse is not RFC 4122 wire order. The handler must swap the
  two 64-bit halves. Getting this wrong produces silently corrupted IDs.
- `DateTime64` timezone belongs to the *column type*, not the session. Never use
  `ZoneId.systemDefault()`.
- `FixedString` null-padding must be stripped on read, or every value gains trailing `\0`.

---

## 5. Protocol Layer

```java
public final class RowBinaryWriter<T> implements Closeable {
    RowBinaryWriter(EntityMetadata<T> md, ChOutputStream out);
    void writeRow(T entity) throws IOException;   // iterate insertableColumns, handler.write
    void writeAll(Collection<? extends T> rows) throws IOException;
    long bytesWritten();
}

public final class RowBinaryReader implements Closeable {
    boolean next() throws IOException;
    <V> V read(int columnIndex, TypeHandler<V,?> handler) throws IOException;
    ChColumnDescriptor[] header();                // from RowBinaryWithNamesAndTypes
}
```

- Write format: `RowBinary` (header-free, we control column order).
- Read format: `RowBinaryWithNamesAndTypes` — the header lets `EntityRowMapper` bind by name
  and detect drift at query time rather than mis-mapping silently.
- `LEB128` varint helper for string/array lengths.
- Buffers pooled per flusher thread; target block size configurable (default 32 MB), aligned
  with ClickHouse's preference for large inserts.

---

## 6. Query AST & Renderer

```java
public final class SelectStatement {
    List<SelectItem> select; TableRef from; boolean useFinal;
    Optional<SampleClause> sample; Optional<Predicate> prewhere; Optional<Predicate> where;
    List<Expression> groupBy; GroupModifier groupModifier;   // NONE | ROLLUP | CUBE | TOTALS
    Optional<Predicate> having; List<JoinClause> joins; List<ArrayJoinClause> arrayJoins;
    List<OrderSpec> orderBy; Optional<LimitBy> limitBy;
    Optional<Long> limit, offset; List<CteDefinition> ctes; Map<String,Object> settings;
}

public sealed interface Expression
    permits ColumnRef, Literal, ParameterRef, FunctionCall, BinaryOp, UnaryOp,
            CaseExpression, SubqueryExpression, WindowFunction, Cast, Lambda { }

public sealed interface Predicate extends Expression
    permits And, Or, Not, Comparison, Between, In, Like, IsNull, Exists { }
```

### 6.1 `SqlRenderer` contract

```java
public final class SqlRenderer {
    BoundStatement render(SelectStatement stmt);
}

public record BoundStatement(String sql, Map<String, Object> parameters) {}
```

**Invariant (NFR-6, security-critical):** the renderer emits `{name:Type}` placeholders for
every `ParameterRef`. `Literal` nodes may only be produced internally from constants that
never originate in user input. A `LiteralFromUserInput` node type does not exist. A CI test
fuzzes user values containing `'; DROP …` and asserts they never appear in the rendered SQL.

Identifier quoting: backticks, with a validation regex `^[A-Za-z_][0-9A-Za-z_]*$` applied
before quoting; anything else is rejected rather than escaped.

### 6.2 Fluent DSL

```java
List<HourlyUniques> rows = ChQuery.from(PageView_.TABLE)
    .select(Functions.toStartOfHour(PageView_.TS).as("hour"),
            Functions.uniq(PageView_.USER_ID).as("uniques"),
            Functions.quantile(0.95, PageView_.DURATION_MS).as("p95"))
    .prewhere(PageView_.TENANT_ID.eq(tenantId))
    .where(PageView_.TS.between(from, to)
       .and(PageView_.COUNTRY.in("IN", "US")))
    .groupBy(col("hour"))
    .orderBy(col("hour").asc())
    .settings(Map.of("max_execution_time", 30))
    .into(HourlyUniques.class)
    .fetch(template);
```

Without the annotation processor the same query uses `col("ts")`, `col("user_id")` — the
DSL never *requires* generated classes (ADR-05).

### 6.3 Generated metamodel

For `PageView`, `clickorm-processor` emits:

```java
public final class PageView_ {
    public static final TableRef TABLE = TableRef.of("analytics", "page_views");
    public static final Column<PageView, Integer> TENANT_ID = Column.of(TABLE, "tenant_id", Integer.class);
    public static final Column<PageView, Instant> TS        = Column.of(TABLE, "ts", Instant.class);
    public static final Column<PageView, UUID>    USER_ID   = Column.of(TABLE, "user_id", UUID.class);
    // …
}
```

`Column<E,V>` exposes typed predicates: `eq(V)`, `in(V…)`, `between(V,V)`, `gt(V)`,
`isNull()`, `like(String)` — so `TS.eq("hello")` fails at compile time.

---

## 7. `ChTemplate` — Core Facade

```java
public interface ChOperations {
    <T> List<T> query(String sql, Map<String,Object> params, RowMapper<T> mapper);
    <T> ChStream<T> stream(String sql, Map<String,Object> params, RowMapper<T> mapper);
    <T> Optional<T> queryForObject(String sql, Map<String,Object> params, RowMapper<T> mapper);
    long execute(String sql, Map<String,Object> params);

    <T> List<T> query(SelectStatement stmt, RowMapper<T> mapper);
    <T> ChStream<T> stream(SelectStatement stmt, RowMapper<T> mapper);

    <T> long insert(Class<T> type, Collection<? extends T> rows);
    <T> BatchWriter<T> batchWriter(Class<T> type, BatchWriterConfig cfg);
}

public final class ChTemplate implements ChOperations, Closeable {
    public static Builder builder(String baseUrl);   // baseUrl includes credentials
    public static final class Builder {
        Builder registry(TypeRegistry r);
        Builder metrics(ChMetrics m);
        Builder tracer(ChTracer t);
        Builder queryIdGenerator(QueryIdGenerator g);
        /** Override the connection pool (default: ChConnectionPoolConfig.defaults()). */
        Builder pool(ChConnectionPoolConfig poolConfig);
        ChTemplate build();
    }
    /** Closes the shared Apache HC5 connection pool. Spring calls this on context shutdown. */
    @Override void close() throws IOException;
    /** Force-merge MergeTree family table: OPTIMIZE TABLE … FINAL [ON CLUSTER]. */
    <T> void optimize(Class<T> entityClass, boolean onCluster) throws ChException;
}
```

### 7.1 Connection pool — `ChConnectionPoolConfig` + `ChHttpClientFactory`

```java
public final class ChConnectionPoolConfig {
    // immutable value object; construct via builder()
    int maxTotal();              // default 200
    int maxPerRoute();           // default 50
    Duration connectTimeout();   // default 5s
    Duration socketTimeout();    // default 60s (generous for OPTIMIZE TABLE)
    Duration idleEvictAfter();   // default 30s
    Duration evictorInterval();  // default 5s (set to ZERO to disable evictor thread)
    Duration validateAfterInactivity(); // default 10s

    static ChConnectionPoolConfig defaults();
    static Builder builder();
}

// Factory — do not call the PoolingHttpClientConnectionManager 4-arg constructor directly;
// use the builder to get proper socket-factory registration.
public final class ChHttpClientFactory {
    public static CloseableHttpClient create(ChConnectionPoolConfig config);
}
```

**Pool sharing model:** `ChTemplate` owns one `CloseableHttpClient`. `batchWriter()` passes
that same client to each `BatchWriter` — no per-writer pool allocation. The 3-arg convenience
constructor on `BatchWriter` creates its own pool (for standalone/test use only).

**Lifecycle:** `ChTemplate.close()` → `http.close()` → pool shutdown. In Spring Boot the bean
implements `Closeable`, so the context calls `close()` automatically on shutdown.

**Error tunneling:** Apache HC5's response-handler lambda only allows `IOException`. ClickHouse
HTTP errors (status ≥ 400) are wrapped as `IOException("__ch_error__:status:body")` and
unwrapped by `rethrowChException()` back into the typed `ChException` hierarchy.

**Execution pipeline** (one method, `executeQuery`, used by every read path):

```
1. QueryContext ctx = new QueryContext(queryId, sql, params, deadline)
2. span = tracer.startSpan(ctx)                    // sanitised SQL only
3. conn = connectionProvider.acquire(deadline)
4. try {
5.     response = conn.query(ctx)                  // sets query_id, settings, timeout
6.     return new ChStream<>(response, mapper, conn)   // conn released on stream close
7. } catch (Exception e) {
8.     throw exceptionTranslator.translate(e, ctx)
9. } finally {
10.    metrics.recordQuery(ctx, outcome, rowsRead, bytesRead)
11.    span.end()
12. }
```

`ChStream<T> extends Stream<T>, Closeable`. A leak detector (phantom-reference based) logs
at WARN with the originating stack trace when a stream is GC'd unclosed.

---

## 8. Row Mapping

```java
@FunctionalInterface
public interface RowMapper<T> {
    T map(RowBinaryReader reader, int rowNum) throws IOException;
    default void init(ChColumnDescriptor[] header) {}   // bind by name, once per result set
}
```

`EntityRowMapper<T>` — `init()` builds an `int[] columnIndexToProperty` from the result-set
header; `map()` is then an index-driven loop with zero name lookups. For records it collects
values into an `Object[]` and invokes the canonical constructor `MethodHandle`; for beans it
invokes per-property setter handles.

`RecordRowMapper<R>` — same, but tolerant of column-count mismatch when the record has fewer
components than the projection (extra columns ignored, missing ones fail loudly).

**Drift detection:** if the header declares a ClickHouse type incompatible with the bound
handler, fail with a message naming the column, the server type, and the expected Java type —
rather than reading garbage.

---

## 9. Write Path

```java
public final class BatchWriter<T> implements AutoCloseable {
    void add(T row);                       // may block per BackpressurePolicy
    boolean offer(T row, Duration timeout);
    void addAll(Collection<? extends T> rows);
    CompletableFuture<Void> flush();       // force flush, completes when acked
    BatchWriterStats stats();
    @Override void close();                // drains within drainTimeout, then fails remaining
}

public record BatchWriterConfig(
    int maxRows, long maxBytes, Duration flushInterval,
    int flusherThreads, BackpressurePolicy backpressure,
    RetryPolicy retry, BatchFailureHandler failureHandler,
    boolean asyncInsert, boolean waitForAsyncInsert,
    Function<List<?>, String> deduplicationTokenFn,   // nullable
    Duration drainTimeout
) { public static BatchWriterConfig defaults(); }

public enum BackpressurePolicy { BLOCK, DROP_OLDEST, FAIL }

@FunctionalInterface
public interface BatchFailureHandler {
    void onFailure(List<?> rows, ChException cause, int attempts);
}
```

### 9.1 Flush algorithm

```
producer.add(row):
    if buffer.tryPut(row) fails:
        switch backpressure:
            BLOCK       → buffer.put(row, acquireTimeout); on timeout → ChBackpressureException
            DROP_OLDEST → buffer.evictOldest(); metrics.dropped++; buffer.put(row)
            FAIL        → throw ChBufferFullException
    if buffer.rows() >= maxRows or buffer.bytes() >= maxBytes: signal flusher

flusher loop:
    batch = buffer.drain(maxRows, maxBytes)   // or timer fires at flushInterval
    if batch.isEmpty(): continue
    attempt = 0
    while true:
        try:
            block = RowBinaryWriter.serialise(batch)
            conn.insert(table, block, settings)          // async_insert / dedup token applied
            metrics.recordBatch(batch.size(), block.length, attempt)
            break
        catch e:
            ex = translator.translate(e)
            if classifier.classify(ex) == RETRYABLE and attempt < retry.maxAttempts:
                sleep(backoffWithJitter(attempt++)); continue
            failureHandler.onFailure(batch, ex, attempt)  // never silently dropped
            metrics.deadLettered(batch.size())
            break
```

### 9.2 Error classification (initial table)

| ClickHouse code | Meaning | Class |
|---|---|---|
| 159 | `TIMEOUT_EXCEEDED` | RETRYABLE |
| 202 | `TOO_MANY_SIMULTANEOUS_QUERIES` | RETRYABLE |
| 203 | `NO_FREE_CONNECTION` | RETRYABLE |
| 209 | `SOCKET_TIMEOUT` | RETRYABLE |
| 210 | `NETWORK_ERROR` | RETRYABLE |
| 241 | `MEMORY_LIMIT_EXCEEDED` | RETRYABLE *(with reduced batch size)* |
| 252 | `TOO_MANY_PARTS` | RETRYABLE *(longer backoff — server is merging)* |
| 999 | `KEEPER_EXCEPTION` | RETRYABLE |
| 47 | `UNKNOWN_IDENTIFIER` | TERMINAL |
| 53 | `TYPE_MISMATCH` | TERMINAL |
| 60 | `UNKNOWN_TABLE` | TERMINAL |
| 62 | `SYNTAX_ERROR` | TERMINAL |
| 81 | `UNKNOWN_DATABASE` | TERMINAL |
| 192 | `UNKNOWN_USER` / auth | TERMINAL |
| *any other* | — | TERMINAL *(conservative — see HLD §9)* |

On `MEMORY_LIMIT_EXCEEDED` and `TOO_MANY_PARTS`, the retry halves the batch size for the
retry attempt. This turns the two most common production ingest failures into self-healing
behaviour rather than dead letters.

---

## 10. Schema Subsystem

```java
public interface DdlGenerator {
    String createTable(EntityMetadata<?> md, boolean ifNotExists);
    String createMaterializedView(MaterializedViewMetadata mv);
    List<String> alterStatements(SchemaDiff diff);
}

public interface SchemaIntrospector {
    Optional<LiveTable> describe(String database, String table);
    List<LiveColumn> columns(String database, String table);
    List<LiveIndex> indexes(String database, String table);
}

public sealed interface SchemaChange {
    boolean destructive();
    record AddColumn(String col, ClickHouseType type, String after)  implements SchemaChange {}
    record DropColumn(String col)                                    implements SchemaChange {}
    record ModifyColumnType(String col, ClickHouseType from,
                            ClickHouseType to)                       implements SchemaChange {}
    record AddIndex(SkipIndexMetadata idx)                           implements SchemaChange {}
    record DropIndex(String name)                                    implements SchemaChange {}
    record EngineMismatch(String expected, String actual)            implements SchemaChange {}
    record OrderByMismatch(List<String> expected, List<String> actual) implements SchemaChange {}
    record TtlMismatch(String expected, String actual)               implements SchemaChange {}
}
```

`destructive()` returns `true` for `DropColumn`, `DropIndex`, and any `ModifyColumnType`
that is not a documented widening (e.g. `UInt32 → UInt64` is safe; `String → UInt32` is not).
`EngineMismatch` and `OrderByMismatch` are **never** auto-fixable — they require a table
rebuild, so `SchemaManager` reports them and refuses.

### 10.1 `SchemaManager.apply()` decision table

| Mode | Table missing | Non-destructive drift | Destructive drift | Unfixable (engine/orderBy) |
|---|---|---|---|---|
| `NONE` | ignore | ignore | ignore | ignore |
| `VALIDATE` | **fail** | **fail** | **fail** | **fail** |
| `CREATE_IF_MISSING` | create | log WARN | log WARN | log WARN |
| `UPDATE` | create | apply `ALTER` | **fail** unless `allowDestructive` | **fail** always |

`VALIDATE` failure prints a table, not a stack trace:

```
ClickORM schema validation failed for analytics.page_views
  ✗ column `country`      expected LowCardinality(String)  actual String
  ✗ column `experiments`  MISSING on server
  ✗ ORDER BY              expected (tenant_id, toDate(ts), user_id)  actual (tenant_id, ts)
  → ORDER BY changes require a table rebuild; ClickORM will not attempt this.
```

---

## 11. Spring Data Integration

```java
@NoRepositoryBean
public interface ClickHouseRepository<T, ID> extends Repository<T, ID> {
    long insert(T entity);                       // instrumented anti-pattern (P2)
    long insertAll(Collection<? extends T> entities);
    List<T> findAll();
    List<T> findAll(Sort sort);
    Slice<T> findAll(Cursor cursor);
    Stream<T> streamAll();
    long count();
    boolean existsBy(/* derived */);
}
```

### 11.1 Repository proxy construction

```
ChRepositoryRegistrar (from @EnableClickHouseRepositories)
    → scans for ClickHouseRepository subinterfaces
    → registers ClickHouseRepositoryFactoryBean per interface
        → ClickHouseRepositoryFactory
            → getTargetRepository()  → SimpleClickHouseRepository(metadata, template)
            → getQueryLookupStrategy() → ChQueryLookupStrategy
                 ├─ @Query present            → NativeChQuery
                 └─ otherwise                 → PartTreeChQuery
```

### 11.2 `PartTree` → AST translation

| Keyword | AST node | Notes |
|---|---|---|
| `And` / `Or` | `And` / `Or` | |
| `Between` | `Between` | |
| `LessThan`, `GreaterThan`, `…Equal` | `Comparison` | |
| `In` / `NotIn` | `In` | collection bound as a ClickHouse `Array` parameter |
| `Like`, `StartingWith`, `EndingWith`, `Containing` | `Like` | pattern built server-side via `concat`, never string-interpolated |
| `IsNull` / `IsNotNull` | `IsNull` | rejected at startup if the column is not `Nullable` |
| `True` / `False` | `Comparison` | |
| `OrderBy…Asc/Desc` | `OrderSpec` | |
| `Top`/`First` | `limit` | |
| `Distinct` | `DISTINCT` | |
| `IgnoreCase` | `lower()` wrapper | warns — defeats index usage |

**Rejected at startup, not runtime:** property paths that don't exist, `IsNull` on
non-nullable columns, nested property traversal (P6 — there are no relations). Failing at
context refresh rather than first invocation is a deliberate choice.

### 11.3 Pagination

```java
public record Cursor(Map<String,Object> after, int size, Sort sort) {
    public static Cursor first(int size, Sort sort);
    public Cursor next(Map<String,Object> lastRowKeys);
}
```

Keyset implementation: append `WHERE (k1,k2) > (:a1,:a2)` derived from the sort spec, fetch
`size + 1` rows, trim, set `hasNext`. **No `COUNT(*)` is ever issued for a `Slice`.**

Offset pagination (`Pageable`) is supported for familiarity but:
- logs at WARN once per method when `offset > clickorm.query.offset-warning-threshold`,
- increments `clickorm.query.deep_offset` counter,
- documented in the Javadoc as an anti-pattern with the keyset alternative shown.

---

## 12. Auto-Configuration

```java
@AutoConfiguration
@ConditionalOnClass(ChTemplate.class)
@EnableConfigurationProperties(ClickOrmProperties.class)
public class ClickOrmAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    ChConnectionProvider chConnectionProvider(ClickOrmProperties p) { … }

    @Bean @ConditionalOnMissingBean
    TypeRegistry chTypeRegistry(ObjectProvider<TypeHandler<?,?>> userHandlers) { … }

    @Bean @ConditionalOnMissingBean
    ChTemplate chTemplate(ChConnectionProvider cp, TypeRegistry tr,
                          ObjectProvider<ChMetrics> metrics,
                          ObjectProvider<ChTracer> tracer,
                          ClickOrmProperties p) { … }

    @Bean @ConditionalOnMissingBean
    SchemaManager chSchemaManager(ChTemplate t, ClickOrmProperties p) { … }

    @Bean @ConditionalOnProperty(prefix="clickorm.schema", name="mode",
                                 havingValue="NONE", matchIfMissing=false)
    InitializingBean chSchemaInitializer(SchemaManager sm) { … }
}
```

Bean ordering constraint: `SchemaManager` must run **before** any `BatchWriter` bean is
created, or the first insert can race table creation. Enforced with `@DependsOn` and covered
by an integration test.

---

## 13. Observability

```java
public interface ChMetrics {
    void recordQuery(QueryContext ctx, Outcome outcome, long rowsRead, long bytesRead);
    void recordBatch(String table, int rows, long bytes, int attempts);
    void recordDeadLetter(String table, int rows);
    void recordBufferOccupancy(String table, int rows, long bytes);
}
```

| Metric | Type | Tags |
|---|---|---|
| `clickorm.query.duration` | Timer | `table`, `operation`, `repository`, `method`, `outcome` |
| `clickorm.query.rows` | DistributionSummary | `table` |
| `clickorm.query.deep_offset` | Counter | `repository`, `method` |
| `clickorm.insert.rows` | Counter | `table` |
| `clickorm.insert.batch.size` | DistributionSummary | `table` |
| `clickorm.insert.singlerow` | Counter | `table` *(anti-pattern signal, R-1)* |
| `clickorm.insert.retries` | Counter | `table`, `error_code` |
| `clickorm.insert.dead_letter.rows` | Counter | `table` |
| `clickorm.buffer.rows` / `.bytes` | Gauge | `table` |
| `clickorm.connection.active` / `.idle` / `.pending` | Gauge | `datasource` |

`query_id` format: `clickorm-{appName}-{traceId|uuid}` — so a user can run
`SELECT * FROM system.query_log WHERE query_id = …` from a trace ID (NFR-10).

---

## 14. Testing Design

| Layer | Approach |
|---|---|
| Type handlers | Property-based round-trip (jqwik): random Java value → write → read → assert equality, across the full type matrix. |
| Type parser | `parse(render(t)) == t` over a generated corpus. |
| SQL renderer | Golden-file snapshot tests; diffs are reviewed, not auto-accepted. |
| SQL injection | Fuzz corpus of adversarial strings asserted never to appear in rendered SQL. |
| Metadata resolver | Negative tests asserting exact error messages (they are part of the UX). |
| Batch writer | Chaos tests: container kill mid-flush, network partition, `SIGTERM` with a full buffer. |
| Repositories | Testcontainers integration per derived-keyword. |
| Schema | Generate → create → introspect → diff → assert empty, for every engine. |
| Performance | JMH gates in CI; regression >10% fails the build (NFR-1, NFR-2). |
| Compatibility | Matrix build: ClickHouse 24.3/24.8/latest × Spring Boot 3.2–3.4 × Java 17/21. |
| Architecture | ArchUnit: no Spring imports in `core`; no `…internal.*` leakage into public signatures. |

---

## 15. Implementation Order (maps to `02-PHASED-PLAN.md`)

```
Phase 1 : type/ → protocol/ → connection/ → metadata/ → mapping/ → template/ → exception/
Phase 2 : write/ (RowBinaryWriter → BatchWriter → RetryPolicy → BatchFailureHandler)
Phase 3 : schema/ (DdlGenerator → SchemaIntrospector → SchemaDiff → SchemaManager)
Phase 4 : spring/repository → spring/config → boot/
Phase 5 : query/ast → SqlRenderer → ChQuery → processor/
Phase 6 : observability/
Phase 7 : advanced engines, MV, dictionaries, JSON/Variant, MutationOperations
```

Note the deliberate ordering within Phase 1: **`type/` first.** Every other component
depends on the type system, and a mistake there (the `UUID` byte order, the `DateTime64`
timezone) propagates into silent data corruption everywhere else. It is the highest-risk,
highest-leverage component in the codebase and deserves to be built and property-tested
before anything else exists.
