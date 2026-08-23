package io.blinkhouse.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Programmatic JMH runner for quick local runs without building the fat jar.
 *
 * <p>Run from your IDE or via Maven exec plugin:
 * <pre>
 *   mvn -pl blinkhouse-benchmark compile exec:java \
 *       -Dexec.mainClass=io.blinkhouse.benchmark.SpikeARunner
 * </pre>
 *
 * <p>Or, for a full production run with the uber-jar:
 * <pre>
 *   mvn -pl blinkhouse-benchmark package -DskipTests
 *   java -jar blinkhouse-benchmark/target/benchmarks.jar SpikeA
 * </pre>
 *
 * <p>Results are written to {@code spike-a-results.json} (JSON) alongside
 * the summary printed to stdout.
 */
public final class SpikeARunner {

    public static void main(String[] args) throws Exception {
        // Start ClickHouse via Testcontainers BEFORE JMH forks worker JVMs.
        // The container URL is propagated as a system property so forked JVMs find it.
        System.out.println("[SpikeARunner] Starting ClickHouse container...");
        BenchmarkContainer.startContainer();
        System.out.println("[SpikeARunner] Container ready at http://"
                + BenchmarkContainer.host() + ":" + BenchmarkContainer.httpPort());

        Options opts = new OptionsBuilder()
                // Match only the Spike A benchmark class.
                .include(SpikeABenchmark.class.getSimpleName())

                // Lighter settings for a quick local run (~30 min vs full 2 h run).
                .warmupIterations(2)
                .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(10))
                .measurementIterations(3)
                .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(20))
                .forks(1)

                // Persist results for ADR-04 evidence.
                .resultFormat(ResultFormatType.JSON)
                .result("spike-a-results.json")

                // Surface GC pressure and allocation rates.
                .jvmArgsAppend(
                        "-Xms512m",
                        "-Xmx1g",
                        // Pass container URL to forked JVM — set by startContainer() above.
                        "-Dbh.clickhouse.url=" + System.getProperty("bh.clickhouse.url", "http://localhost:8123"),
                        "-Dbh.clickhouse.user=" + BenchmarkContainer.USER,
                        "-Dbh.clickhouse.password=" + BenchmarkContainer.PASSWORD
                )
                .build();

        new Runner(opts).run();
    }
}
