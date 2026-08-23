package io.blinkhouse.core.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared, statically reused ClickHouse container for integration tests.
 *
 * <p>All ITs in {@code blinkhouse-core} share one container instance — Testcontainers'
 * Ryuk reaper cleans it up after the JVM exits. This keeps the full IT suite well
 * under the 5-minute Phase 0 exit criterion.
 *
 * <p>The ClickHouse image version is resolved from the {@code BH_CLICKHOUSE_IMAGE}
 * environment variable (set by CI for the version matrix) with a default of
 * {@code clickhouse/clickhouse-server:24.8} for local runs.
 *
 * <p>Usage — annotate your IT class:
 * <pre>{@code
 * @Testcontainers
 * class MyIT {
 *     @Container
 *     static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;
 * }
 * }</pre>
 *
 * <p><b>Note:</b> declare the container as {@code static} so Testcontainers reuses it
 * across all test methods in the class. The {@code @Container} annotation on a
 * non-static field starts and stops the container per test method.
 */
public final class ClickHouseContainerExtension {

    public static final String USER     = "bh_test";
    public static final String PASSWORD = "bh_test";
    public static final String DATABASE = "default";

    /** Shared container instance — started once, reused across all ITs in the JVM. */
    public static final GenericContainer<?> INSTANCE;

    static {
        String image = System.getenv().getOrDefault(
                "BH_CLICKHOUSE_IMAGE", "clickhouse/clickhouse-server:24.8");

        INSTANCE = new GenericContainer<>(DockerImageName.parse(image))
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
        INSTANCE.start();
    }

    private ClickHouseContainerExtension() {}

    /** Base HTTP URL including credentials, ready for query-string appending. */
    public static String baseUrl() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(8123)
                + "/?user=" + USER + "&password=" + PASSWORD + "&database=" + DATABASE;
    }

    public static String host() {
        return INSTANCE.getHost();
    }

    public static int httpPort() {
        return INSTANCE.getMappedPort(8123);
    }
}
