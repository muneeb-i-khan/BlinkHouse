# Coming from JPA / Spring Data JPA

If you've used Spring Data JPA, you'll find BlinkHouse familiar in structure but different in semantics. This page maps JPA concepts to BlinkHouse equivalents and calls out the places where the analogy breaks.

---

## Entity mapping

| JPA | BlinkHouse |
|-----|-----------|
| `@Entity` | `@ChTable` |
| `@Table(name="…")` | `@ChTable(name="…", database="…")` |
| `@Column` | `@ChColumn` |
| `@Id` | No equivalent — ClickHouse has no primary key constraint; use `orderBy` in `@ChEngine` |
| `@Transient` | `@ChIgnore` |
| `@OneToMany` / `@ManyToOne` | **Not supported** — ClickHouse has no FK concept; join in SQL |
| `@GeneratedValue` | Not supported — generate IDs in application code (`UUID.randomUUID()`) |

**Key difference:** BlinkHouse does not manage identity. There is no session, no first-level cache, and no dirty checking. Every `insert()` call writes to ClickHouse immediately.

---

## Repositories

```java
// JPA
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByEmailOrderByCreatedAtDesc(String email);
}

// BlinkHouse
public interface EventRepository extends ChRepository<PageViewEvent> {
    @Query("SELECT * FROM {table} WHERE user_id = {userId} ORDER BY ts DESC LIMIT 100")
    List<PageViewEvent> findByUserId(@Param("userId") String userId);
}
```

`ChRepository` does not extend `PagingAndSortingRepository` or `CrudRepository` — there is no `save()` because writes always go through `BatchWriter`. Use `ChTemplate.insert()` for one-off writes and `ChTemplate.batchWriter()` for throughput.

---

## Writes

| JPA | BlinkHouse |
|-----|-----------|
| `repository.save(entity)` | `template.insertSingleRow(entity)` (anti-pattern — logs WARN) |
| `repository.saveAll(list)` | `template.insert(EntityClass.class, list)` |
| Production bulk ingest | `template.batchWriter(EntityClass.class, config)` |

> **Why no `save()`?** In JPA, `save()` hits a single row via JDBC PreparedStatement — acceptable for OLTP. In ClickHouse, per-row inserts are an anti-pattern that creates too many parts and degrades merge performance. BlinkHouse deliberately makes batching the default path.

---

## Reads

| JPA | BlinkHouse |
|-----|-----------|
| `findById(id)` | `template.queryForList(T.class, "SELECT … WHERE id = …")` |
| `findAll()` | `template.queryForList(T.class, "SELECT * FROM table LIMIT 10000")` |
| `@Query(nativeQuery=true, …)` | `@Query("…")` (all queries are native SQL) |
| `Page<T> findAll(Pageable)` | `LIMIT n OFFSET m` or keyset pagination via DSL |
| `@EntityGraph` | Not applicable — no lazy loading |

---

## Transactions

**There are no transactions in BlinkHouse.** ClickHouse does not support ACID transactions across rows (experimental transactional tables are a separate topic not covered by this framework). Do not put `@Transactional` on methods that call BlinkHouse — it does nothing and misleads readers.

---

## Schema management

| JPA | BlinkHouse |
|-----|-----------|
| `spring.jpa.hibernate.ddl-auto=create` | `blinkhouse.schema.mode=CREATE_IF_MISSING` |
| `spring.jpa.hibernate.ddl-auto=validate` | `blinkhouse.schema.mode=VALIDATE` |
| `spring.jpa.hibernate.ddl-auto=update` | `blinkhouse.schema.mode=UPDATE` (requires explicit opt-in) |
| Flyway / Liquibase migrations | `SchemaManager.generateMigrationScript()` produces SQL to review and apply manually |

BlinkHouse **never auto-applies destructive changes** (column drops, table rebuilds) without `allowDestructive=true`. This is intentional — ClickHouse DDL changes have much larger blast radius than RDBMS DDL.

---

## What BlinkHouse deliberately does not do

- No dirty checking or change tracking
- No first-level or second-level cache
- No `@OneToMany`, `@ManyToOne`, `@ManyToMany`, or any join mapping
- No `@Transactional` support (no-op and misleading — don't use it)
- No optimistic or pessimistic locking (`@Version`)
- No `EntityManager` / session concept
- No JPQL — all queries are native ClickHouse SQL
- No `Specification` or `Criteria` API (use the typed `ChQuery` DSL instead)
- No `Pageable` / `Sort` parameter injection (build `ORDER BY` and `LIMIT` in the query DSL)

These omissions are not oversights — they reflect the ClickHouse data model. Adding them would either be a lie (pretending ClickHouse supports semantics it doesn't) or a performance trap.
