package io.blinkhouse.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Programmatic JMH runner for the transport comparison benchmark.
 *
 * <p>Starts a ClickHouse Testcontainer before JMH forks its worker JVMs, then
 * propagates the container URL as a system property so each forked JVM can
 * connect. Call this instead of running the fat jar directly when you want
 * Docker managed automatically.
 *
 * <p>Quick local run via Maven:
 * <pre>
 *   mvn -pl blinkhouse-benchmark compile exec:java \
 *       -Dexec.mainClass=io.blinkhouse.benchmark.TransportBenchmarkRunner
 * </pre>
 *
 * <p>Full run against an existing ClickHouse instance:
 * <pre>
 *   mvn -pl blinkhouse-benchmark package -DskipTests
 *   java -Dbh.clickhouse.url=http://localhost:8123 \
 *        -jar blinkhouse-benchmark/target/benchmarks.jar TransportBenchmark
 * </pre>
 *
 * <p>Results are written to {@code transport-benchmark-results.json}.
 */
public final class TransportBenchmarkRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("[TransportBenchmarkRunner] Starting ClickHouse container...");
        BenchmarkContainer.startContainer();
        System.out.println("[TransportBenchmarkRunner] Container ready at http://"
                + BenchmarkContainer.host() + ":" + BenchmarkContainer.httpPort());

        Options opts = new OptionsBuilder()
                .include(TransportBenchmark.class.getSimpleName())
                .warmupIterations(2)
                .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(10))
                .measurementIterations(3)
                .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(20))
                .forks(1)
                .resultFormat(ResultFormatType.JSON)
                .result("transport-benchmark-results.json")
                .jvmArgsAppend(
                        "-Xms512m",
                        "-Xmx1g",
                        "-Dbh.clickhouse.url=" + System.getProperty("bh.clickhouse.url", "http://localhost:8123"),
                        "-Dbh.clickhouse.user=" + BenchmarkContainer.USER,
                        "-Dbh.clickhouse.password=" + BenchmarkContainer.PASSWORD
                )
                .build();

        new Runner(opts).run();
    }
}
