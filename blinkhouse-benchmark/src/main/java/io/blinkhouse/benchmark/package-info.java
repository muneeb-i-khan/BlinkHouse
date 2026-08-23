/**
 * JMH benchmark suite — not published to Maven Central.
 *
 * <p>These benchmarks are CI gates. A regression of more than 10% in either
 * NFR-1 or NFR-2 fails the build.
 *
 * <ul>
 *   <li>NFR-1 (write): BlinkHouse bulk insert throughput ≥ 90% of a hand-tuned
 *       {@code clickhouse-java} client benchmark on the same hardware and schema.</li>
 *   <li>NFR-2 (read): row-mapping overhead ≤ 15% vs raw {@code ResultSet} iteration
 *       for a 20-column, 1M-row scan.</li>
 * </ul>
 *
 * <p>Run with: {@code java -jar target/benchmarks.jar}
 */
package io.blinkhouse.benchmark;
