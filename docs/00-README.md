# ClickORM — Design Document Set

A ClickHouse-native persistence framework for Java and Spring Boot.

| # | Document | Read it for |
|---|----------|-------------|
| 1 | [`01-REQUIREMENTS.md`](01-REQUIREMENTS.md) | Scope, 69 numbered requirements (46 must-have), explicit non-goals, risks, success criteria, open questions. |
| 2 | [`02-PHASED-PLAN.md`](02-PHASED-PLAN.md) | 9 phases with effort estimates, exit criteria, dependency graph, go/no-go gates, and a recommended "Lean v1.0" cut. |
| 3 | [`03-HLD.md`](03-HLD.md) | Module decomposition, layering, data flows, 10 architecture decision records, concurrency & security model. |
| 4 | [`04-LLD.md`](04-LLD.md) | Annotations, class signatures, type-mapping table, algorithms, error-code classification, implementation order. |

## The one-paragraph version

ClickHouse has no credible JVM ORM because JPA's contract — transactions, row identity,
dirty checking, lazy relations — is unsatisfiable on a columnar OLAP store. ClickORM's
thesis is that the useful half of the ORM experience (declarative schema, typed queries,
repository interfaces, Spring Boot auto-configuration) can be delivered *without* the
unsatisfiable half, and that being explicit about the missing half is a feature rather than
an apology.

## Effort at a glance

| Option | Scope | Full-time | Part-time (~10h/wk) |
|--------|-------|----------:|--------------------:|
| **Lean v1.0** *(recommended)* | Phases 0–4, 6, 8 | ~26 weeks | ~14 months |
| **Full v1.0** | Phases 0–8 | ~40 weeks | ~24 months |

The Lean cut defers the compile-time metamodel (Phase 5) and advanced ClickHouse features
(Phase 7) to 1.x. Both are purely additive, so deferring them breaks nothing — and Phase 5
is the most expensive feature per unit of adoption in the whole plan.

## The four decisions that matter most

1. **Do not implement a Hibernate dialect.** (HLD ADR-01) Every workaround is a lie about
   semantics, and users will build production systems on top of the lie.
2. **Keep `clickorm-core` free of Spring.** (HLD ADR-02) It widens the audience, forces
   clean boundaries, and insulates you from Spring Data SPI churn.
3. **Build the type system first and property-test it to death.** (LLD §15) A bug in the
   `UUID` byte ordering or `DateTime64` timezone handling is silent data corruption
   everywhere else in the library.
4. **Get an external adopter by Phase 4.** (Plan §13) If nobody uses the starter within
   three months of release, stop and find out why before spending 11 more weeks on
   Phases 5 and 7.

## Suggested reading order

- **Deciding whether to build this at all** → `01` §4 (scope), `02` §11 (lean cut), `02` §13 (kill criteria).
- **Reviewing the architecture** → `03` in full, then `01` §6 (NFRs).
- **Starting to write code** → `04` §15 (implementation order), then `04` §2–4 (annotations and types).
