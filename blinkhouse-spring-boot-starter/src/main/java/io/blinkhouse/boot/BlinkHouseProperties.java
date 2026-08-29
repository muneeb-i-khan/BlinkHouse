package io.blinkhouse.boot;

import io.blinkhouse.core.schema.SchemaMode;
import io.blinkhouse.core.write.BackpressurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for BlinkHouse, bound under the {@code clickhouse} prefix.
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * clickhouse:
 *   url: http://localhost:8123
 *   username: default
 *   password: ""
 *   database: default
 *   schema:
 *     mode: VALIDATE
 *   batch:
 *     max-rows: 100000
 * }</pre>
 */
@ConfigurationProperties(prefix = "clickhouse")
public class BlinkHouseProperties {

    /** ClickHouse HTTP URL, e.g. {@code http://localhost:8123}. */
    private String url = "http://localhost:8123";

    /** ClickHouse username. */
    private String username = "default";

    /** ClickHouse password. */
    private String password = "";

    /** Default database. */
    private String database = "default";

    /**
     * Application name embedded in {@code query_id} for {@code system.query_log} correlation.
     * Defaults to {@code "app"} if unset.
     */
    private String appName;

    /** Query-level settings. */
    private final QueryProperties query = new QueryProperties();

    /** Batch write settings. */
    private final BatchProperties batch = new BatchProperties();

    /** Schema management settings. */
    private final SchemaProperties schema = new SchemaProperties();

    /** Observability settings. */
    private final MetricsProperties metrics = new MetricsProperties();

    /** HTTP connection pool settings. */
    private final ConnectionPoolProperties pool = new ConnectionPoolProperties();

    /** @return the ClickHouse HTTP URL */
    public String getUrl() {
        return url;
    }

    /** @param url the ClickHouse HTTP URL */
    public void setUrl(String url) {
        this.url = url;
    }

    /** @return the ClickHouse username */
    public String getUsername() {
        return username;
    }

    /** @param username the username */
    public void setUsername(String username) {
        this.username = username;
    }

    /** @return the ClickHouse password */
    public String getPassword() {
        return password;
    }

    /** @param password the password */
    public void setPassword(String password) {
        this.password = password;
    }

    /** @return the default database name */
    public String getDatabase() {
        return database;
    }

    /** @param database the database name */
    public void setDatabase(String database) {
        this.database = database;
    }

    /** @return the application name used in query IDs */
    public String getAppName() {
        return appName;
    }

    /** @param appName the application name */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /** @return the query properties */
    public QueryProperties getQuery() {
        return query;
    }

    /** @return the batch write properties */
    public BatchProperties getBatch() {
        return batch;
    }

    /** @return the schema properties */
    public SchemaProperties getSchema() {
        return schema;
    }

    /** @return the metrics properties */
    public MetricsProperties getMetrics() {
        return metrics;
    }

    /**
     * Builds the ClickHouse HTTP base URL including credentials as query parameters.
     *
     * @return the composed base URL
     */
    public String buildBaseUrl() {
        return url + "/?user=" + username
            + "&password=" + password
            + "&database=" + database;
    }

    /** Query-level configuration. */
    public static class QueryProperties {

        /** Default query timeout. */
        private Duration defaultTimeout = Duration.ofSeconds(30);

        /** Offset row count above which a WARN is emitted. */
        private long offsetWarningThreshold = 10_000L;

        /** @return the default timeout */
        public Duration getDefaultTimeout() {
            return defaultTimeout;
        }

        /** @param defaultTimeout the default timeout */
        public void setDefaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }

        /** @return the offset warning threshold */
        public long getOffsetWarningThreshold() {
            return offsetWarningThreshold;
        }

        /** @param offsetWarningThreshold the threshold */
        public void setOffsetWarningThreshold(long offsetWarningThreshold) {
            this.offsetWarningThreshold = offsetWarningThreshold;
        }
    }

    /** Batch write configuration. */
    public static class BatchProperties {

        /** Maximum rows per flush batch. */
        private int maxRows = 100_000;

        /** Maximum bytes per flush batch. */
        private long maxBytes = 32L * 1024 * 1024;

        /** Maximum time between flushes. */
        private Duration flushInterval = Duration.ofSeconds(1);

        /** Number of flusher threads. */
        private int flusherThreads = 2;

        /** Backpressure policy when the buffer is full. */
        private BackpressurePolicy backpressure = BackpressurePolicy.BLOCK;

        /** Timeout for acquiring a buffer slot under BLOCK policy. */
        private Duration acquireTimeout = Duration.ofSeconds(5);

        /** Drain timeout on graceful shutdown. */
        private Duration drainTimeout = Duration.ofSeconds(30);

        /** @return maximum rows per batch */
        public int getMaxRows() {
            return maxRows;
        }

        /** @param maxRows max rows */
        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        /** @return maximum bytes per batch */
        public long getMaxBytes() {
            return maxBytes;
        }

        /** @param maxBytes max bytes */
        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        /** @return flush interval */
        public Duration getFlushInterval() {
            return flushInterval;
        }

        /** @param flushInterval flush interval */
        public void setFlushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
        }

        /** @return number of flusher threads */
        public int getFlusherThreads() {
            return flusherThreads;
        }

        /** @param flusherThreads thread count */
        public void setFlusherThreads(int flusherThreads) {
            this.flusherThreads = flusherThreads;
        }

        /** @return the backpressure policy */
        public BackpressurePolicy getBackpressure() {
            return backpressure;
        }

        /** @param backpressure the policy */
        public void setBackpressure(BackpressurePolicy backpressure) {
            this.backpressure = backpressure;
        }

        /** @return the acquire timeout */
        public Duration getAcquireTimeout() {
            return acquireTimeout;
        }

        /** @param acquireTimeout the timeout */
        public void setAcquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
        }

        /** @return the drain timeout */
        public Duration getDrainTimeout() {
            return drainTimeout;
        }

        /** @param drainTimeout the drain timeout */
        public void setDrainTimeout(Duration drainTimeout) {
            this.drainTimeout = drainTimeout;
        }
    }

    /** Schema management configuration. */
    public static class SchemaProperties {

        /** Schema application mode. */
        private SchemaMode mode = SchemaMode.NONE;

        /** When {@code true}, destructive schema changes (DROP COLUMN) are permitted in UPDATE mode. */
        private boolean allowDestructive = false;

        /** Directory to write migration SQL scripts; empty means do not write. */
        private String emitScriptsTo = "";

        /** @return the schema mode */
        public SchemaMode getMode() {
            return mode;
        }

        /** @param mode the schema mode */
        public void setMode(SchemaMode mode) {
            this.mode = mode;
        }

        /** @return whether destructive changes are allowed */
        public boolean isAllowDestructive() {
            return allowDestructive;
        }

        /** @param allowDestructive the flag */
        public void setAllowDestructive(boolean allowDestructive) {
            this.allowDestructive = allowDestructive;
        }

        /** @return the migration script output directory */
        public String getEmitScriptsTo() {
            return emitScriptsTo;
        }

        /** @param emitScriptsTo the directory path */
        public void setEmitScriptsTo(String emitScriptsTo) {
            this.emitScriptsTo = emitScriptsTo;
        }
    }

    /** Observability configuration. */
    public static class MetricsProperties {

        /** Whether Micrometer metrics are enabled. */
        private boolean enabled = true;

        /** Whether distributed tracing is enabled. */
        private boolean tracingEnabled = true;

        /** @return whether metrics are enabled */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled the flag */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return whether tracing is enabled */
        public boolean isTracingEnabled() {
            return tracingEnabled;
        }

        /** @param tracingEnabled the flag */
        public void setTracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
        }
    }

    /** @return the connection pool properties */
    public ConnectionPoolProperties getPool() {
        return pool;
    }

    /** HTTP connection pool configuration. */
    public static class ConnectionPoolProperties {

        /** Total maximum connections across all routes. */
        private int maxTotal = 200;

        /** Maximum connections per ClickHouse host. */
        private int maxPerRoute = 50;

        /** TCP connect timeout. */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * Socket read/write timeout.
         * Set generously to accommodate long-running OPTIMIZE TABLE calls.
         */
        private Duration socketTimeout = Duration.ofSeconds(60);

        /** Connections idle for longer than this are eligible for eviction. */
        private Duration idleEvictAfter = Duration.ofSeconds(30);

        /**
         * Period between background idle-connection eviction sweeps.
         * Set to zero to disable the eviction thread.
         */
        private Duration evictorInterval = Duration.ofSeconds(5);

        /**
         * Connections inactive for longer than this are validated before being leased
         * (avoids half-open socket errors on long-lived keep-alive connections).
         */
        private Duration validateAfterInactivity = Duration.ofSeconds(10);

        /** @return total max connections */
        public int getMaxTotal() {
            return maxTotal;
        }

        /** @param maxTotal total max connections */
        public void setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
        }

        /** @return max connections per route */
        public int getMaxPerRoute() {
            return maxPerRoute;
        }

        /** @param maxPerRoute max connections per route */
        public void setMaxPerRoute(int maxPerRoute) {
            this.maxPerRoute = maxPerRoute;
        }

        /** @return TCP connect timeout */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /** @param connectTimeout TCP connect timeout */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /** @return socket read/write timeout */
        public Duration getSocketTimeout() {
            return socketTimeout;
        }

        /** @param socketTimeout socket read/write timeout */
        public void setSocketTimeout(Duration socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        /** @return idle eviction threshold */
        public Duration getIdleEvictAfter() {
            return idleEvictAfter;
        }

        /** @param idleEvictAfter idle eviction threshold */
        public void setIdleEvictAfter(Duration idleEvictAfter) {
            this.idleEvictAfter = idleEvictAfter;
        }

        /** @return eviction thread interval */
        public Duration getEvictorInterval() {
            return evictorInterval;
        }

        /** @param evictorInterval eviction thread interval */
        public void setEvictorInterval(Duration evictorInterval) {
            this.evictorInterval = evictorInterval;
        }

        /** @return inactivity period before connection validation */
        public Duration getValidateAfterInactivity() {
            return validateAfterInactivity;
        }

        /** @param validateAfterInactivity inactivity period */
        public void setValidateAfterInactivity(Duration validateAfterInactivity) {
            this.validateAfterInactivity = validateAfterInactivity;
        }
    }
}
