# Changelog

All notable changes to BlinkHouse are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
BlinkHouse follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-08-29

First stable release. API freeze on all types not in `*.internal.*` packages.

### Added — Core (`blinkhouse-core`)
- **Read path**: `ChTemplate.queryForList`, `query(SelectStatement, RowMapper)`, full type registry
- **Write path**: `BatchWriter` with bounded queue, `RowBinaryWriter`, flush on count/bytes/elapsed, backpressure
- **Schema & DDL**: `SchemaManager`, `DdlGenerator`, `SchemaDiff`, `SchemaIntrospector`, migration script writer
- **Query DSL**: `ChQuery` fluent builder, `Functions` library, `SqlRenderer`, `BoundStatement`, typed metamodel via annotation processor
- **Spring Boot Starter**: `@BlinkHouseTest` slice, `BlinkHouseAutoConfiguration`, `BlinkHouseProperties`, Spring Data repository support
- **Observability**: `ChMetrics`/`ChTracer` SPI, Micrometer & OTel implementations, `QueryIdGenerator`, Grafana dashboard, runbook
- **Advanced features**: `@ChMaterializedView`, `@ChDictionary`, `AggregateFunctionHandler`, `-Merge` SQL combinators, `MutationOperations` (`ALTER TABLE … DELETE/UPDATE`), geo types (`GeoPoint`, `GeoRing`, `GeoPolygon`, `GeoMultiPolygon`)
- **Connection pooling**: Apache HttpClient 5 (`httpclient5` 5.4.1) connection pool replacing the bare JDK `HttpClient`. `ChConnectionPoolConfig` value object (maxTotal=200, maxPerRoute=50, connectTimeout=5s, socketTimeout=60s, idle eviction, inactivity validation). `ChHttpClientFactory` builds a `PoolingHttpClientConnectionManager`-backed `CloseableHttpClient` shared across `ChTemplate` and all its `BatchWriter` children. `ChTemplate` now implements `Closeable`. Pool fully configurable via `clickhouse.pool.*` YAML namespace. 8 new unit tests.
- **Hardening**: `@BlinkHouseApi`/`@Internal` stability annotations, `ChTemplate.optimize()` (`OPTIMIZE TABLE … FINAL`), GraalVM native-image hints, ArchUnit API-stability and layering rules, SQL injection audit tests
- **CI**: Java 17 × 21 matrix, ClickHouse 24.3 / 24.8 / 25.1 matrix, benchmark compile check
- **Community**: `CODEOWNERS`, issue templates, `CHANGELOG`

### Type handlers (all with round-trip integration tests)
`UInt8/16/32/64`, `Int8/16/32/64`, `Int128/256`, `Float32/64`, `Decimal`, `String`, `FixedString`,
`UUID`, `DateTime`, `DateTime64`, `Date`, `Date32`, `IPv4`, `IPv6`, `Enum8/16`,
`LowCardinality`, `Nullable`, `Array`, `Map`, `Tuple`, `Point`, `Ring`, `Polygon`, `MultiPolygon`,
`AggregateFunction`

### Breaking changes
None — this is the first stable release.

---

## [0.9.0] — Phase 7 milestone (internal)

Advanced ClickHouse features: materialized views, dictionaries, aggregate functions, geo types, mutation operations.

## [0.6.0] — Phase 6 milestone (internal)

Observability & Resilience: metrics SPI, tracing SPI, Micrometer wiring, Grafana dashboard, runbook.

## [0.4.0] — Phase 4 milestone (internal)

Spring Boot Starter & Repositories: `@BlinkHouseTest`, `BlinkHouseAutoConfiguration`, Spring Data support.

## [0.3.0] — Phase 3 milestone (internal)

Schema & DDL: `SchemaManager`, `DdlGenerator`, `SchemaDiff`, migration tooling.

## [0.2.0] — Phase 2 milestone (internal)

Write path: `BatchWriter`, `RowBinaryWriter`, retry policy, backpressure.

## [0.1.0] — Phase 1 milestone (internal)

Core read path: `ChTemplate`, type registry, `RowBinaryWithNamesAndTypes` deserialization.
