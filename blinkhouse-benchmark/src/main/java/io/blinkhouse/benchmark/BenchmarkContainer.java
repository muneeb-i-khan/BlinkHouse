package io.blinkhouse.benchmark;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

/**
 * ClickHouse connection coordinates for the benchmark suite.
 *
 * <p>Two modes:
 * <ol>
 *   <li><b>External</b> (default for JMH fat-jar runs): set system property
 *       {@code bh.clickhouse.url=http://host:port} before running. Credentials
 *       default to {@code bh_test}/{@code bh_test} but can be overridden with
 *       {@code bh.clickhouse.user} and {@code bh.clickhouse.password}.</li>
 *   <li><b>Testcontainers</b> (for {@link SpikeARunner} in-process runs): call
 *       {@link #startContainer()} once before running benchmarks. This avoids
 *       starting Docker from inside JMH's forked JVM.</li>
 * </ol>
 */
public final class BenchmarkContainer {

    public static final String USER     = "bh_test";
    public static final String PASSWORD = "bh_test";
    public static final String DATABASE = "default";

    private static volatile GenericContainer<?> container;

    /** Host resolved at first call to {@link #host()} / {@link #httpPort()}. */
    private static volatile String resolvedHost;
    private static volatile int    resolvedPort = -1;

    /**
     * Start a Testcontainers ClickHouse instance. Call once from {@link SpikeARunner}
     * before programmatic benchmark execution. Do NOT call from JMH @Setup — the
     * container must be running before JMH forks worker JVMs.
     */
    public static void startContainer() {
        if (container != null) return;
        GenericContainer<?> c = new GenericContainer<>("clickhouse/clickhouse-server:24.8")
                .withExposedPorts(8123)
                .withEnv("CLICKHOUSE_USER",     USER)
                .withEnv("CLICKHOUSE_PASSWORD", PASSWORD)
                .withEnv("CLICKHOUSE_DB",       DATABASE)
                .waitingFor(
                        new HttpWaitStrategy()
                                .forPort(8123)
                                .forPath("/ping")
                                .forResponsePredicate("Ok."::equals)
                );
        c.start();
        container     = c;
        resolvedHost  = c.getHost();
        resolvedPort  = c.getMappedPort(8123);
        // Expose as system properties so JMH forked JVMs can read them
        System.setProperty("bh.clickhouse.url",      "http://" + resolvedHost + ":" + resolvedPort);
        System.setProperty("bh.clickhouse.user",     USER);
        System.setProperty("bh.clickhouse.password", PASSWORD);
    }

    public static String host() {
        resolve();
        return resolvedHost;
    }

    public static int httpPort() {
        resolve();
        return resolvedPort;
    }

    private static void resolve() {
        if (resolvedPort != -1) return;
        String url = System.getProperty("bh.clickhouse.url", "http://localhost:8123");
        // parse host:port from url
        String stripped = url.replace("http://", "").replace("https://", "");
        int colon = stripped.lastIndexOf(':');
        resolvedHost = colon > 0 ? stripped.substring(0, colon) : stripped;
        resolvedPort = colon > 0 ? Integer.parseInt(stripped.substring(colon + 1)) : 8123;
    }

    private BenchmarkContainer() {}
}
