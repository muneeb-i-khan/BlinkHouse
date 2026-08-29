package io.blinkhouse.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers ClickHouse instance for use in {@code @BlinkHouseTest} slices.
 *
 * <p>The container is started once per JVM and shared across all test classes,
 * targeting the &lt; 10-second boot time exit criterion for {@code @BlinkHouseTest}.
 *
 * <p>The ClickHouse image is resolved from the {@code BH_CLICKHOUSE_IMAGE}
 * environment variable, defaulting to {@code clickhouse/clickhouse-server:24.8}.
 */
public final class BhTestContainer {

    /** Default ClickHouse username for tests. */
    public static final String USER = "bh_test";

    /** Default ClickHouse password for tests. */
    public static final String PASSWORD = "bh_test";

    /** Default database for tests. */
    public static final String DATABASE = "default";

    /** The shared singleton container instance. */
    public static final GenericContainer<?> INSTANCE;

    static {
        String image = System.getenv().getOrDefault(
            "BH_CLICKHOUSE_IMAGE", "clickhouse/clickhouse-server:24.8");

        INSTANCE = new GenericContainer<>(DockerImageName.parse(image))
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_USER", USER)
            .withEnv("CLICKHOUSE_PASSWORD", PASSWORD)
            .withEnv("CLICKHOUSE_DB", DATABASE)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(8123)
                    .forPath("/ping")
                    .forResponsePredicate("Ok."::equals)
            );
        INSTANCE.start();
    }

    private BhTestContainer() {
    }

    /**
     * Returns the HTTP base URL including credentials for direct HTTP queries.
     *
     * @return the base URL, e.g. {@code http://localhost:12345/?user=bh_test&...}
     */
    public static String baseUrl() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(8123)
            + "/?user=" + USER + "&password=" + PASSWORD + "&database=" + DATABASE;
    }

    /**
     * Returns the {@code clickhouse.url} property value (scheme + host + port only).
     *
     * @return the URL without credentials
     */
    public static String url() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(8123);
    }
}
