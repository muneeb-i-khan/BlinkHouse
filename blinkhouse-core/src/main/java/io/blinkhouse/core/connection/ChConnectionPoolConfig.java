package io.blinkhouse.core.connection;

import java.time.Duration;

/**
 * Configuration for the Apache HttpClient 5 connection pool used by
 * {@link io.blinkhouse.core.template.ChTemplate} and
 * {@link io.blinkhouse.core.write.BatchWriter}.
 *
 * <p>A single shared pool is created per {@code ChTemplate} instance.
 * {@code BatchWriter} instances borrow from that pool rather than opening
 * their own, so all writers for a given template share the same connection budget.
 *
 * <p>Defaults are sized for a medium-traffic production service hitting a single
 * ClickHouse node. Tune {@link #maxTotal()} and {@link #maxPerRoute()} up for
 * high-throughput ingest or distributed topologies.
 *
 * <p>Build with the fluent {@link Builder}:
 * <pre>{@code
 * ChConnectionPoolConfig pool = ChConnectionPoolConfig.builder()
 *     .maxTotal(200)
 *     .maxPerRoute(50)
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .socketTimeout(Duration.ofSeconds(60))
 *     .build();
 * }</pre>
 */
public final class ChConnectionPoolConfig {

    /** Total maximum open connections in the pool. */
    private final int maxTotal;

    /** Maximum connections per route (effectively per ClickHouse host). */
    private final int maxPerRoute;

    /** TCP connect timeout. */
    private final Duration connectTimeout;

    /** Socket read/write timeout (covers the full request/response). */
    private final Duration socketTimeout;

    /** How long an idle connection is kept before being eligible for eviction. */
    private final Duration idleEvictAfter;

    /**
     * Period between background idle-connection eviction runs.
     * Set to {@link Duration#ZERO} to disable the eviction thread.
     */
    private final Duration evictorInterval;

    /**
     * Connections idle for longer than this are proactively validated before
     * being leased from the pool (triggers a cheap OPTIONS or socket check).
     */
    private final Duration validateAfterInactivity;

    private ChConnectionPoolConfig(Builder b) {
        this.maxTotal = b.maxTotal;
        this.maxPerRoute = b.maxPerRoute;
        this.connectTimeout = b.connectTimeout;
        this.socketTimeout = b.socketTimeout;
        this.idleEvictAfter = b.idleEvictAfter;
        this.evictorInterval = b.evictorInterval;
        this.validateAfterInactivity = b.validateAfterInactivity;
    }

    /** Returns a new {@link Builder} pre-loaded with production-sensible defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a config instance using all defaults — suitable for most deployments. */
    public static ChConnectionPoolConfig defaults() {
        return builder().build();
    }

    /** @return total maximum connections across all routes */
    public int maxTotal() {
        return maxTotal;
    }

    /** @return maximum connections per route */
    public int maxPerRoute() {
        return maxPerRoute;
    }

    /** @return TCP connect timeout */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /** @return socket read/write timeout */
    public Duration socketTimeout() {
        return socketTimeout;
    }

    /** @return idle duration after which a connection is evicted */
    public Duration idleEvictAfter() {
        return idleEvictAfter;
    }

    /** @return interval between eviction runs ({@link Duration#ZERO} = disabled) */
    public Duration evictorInterval() {
        return evictorInterval;
    }

    /** @return inactivity period before a connection is validated before lease */
    public Duration validateAfterInactivity() {
        return validateAfterInactivity;
    }

    /** Fluent builder for {@link ChConnectionPoolConfig}. */
    public static final class Builder {

        private int maxTotal = 200;
        private int maxPerRoute = 50;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration socketTimeout = Duration.ofSeconds(60);
        private Duration idleEvictAfter = Duration.ofSeconds(30);
        private Duration evictorInterval = Duration.ofSeconds(5);
        private Duration validateAfterInactivity = Duration.ofSeconds(10);

        private Builder() {
        }

        /**
         * Total maximum connections across all routes.
         *
         * @param maxTotal must be &gt;= 1
         * @return this builder
         */
        public Builder maxTotal(int maxTotal) {
            if (maxTotal < 1) {
                throw new IllegalArgumentException("maxTotal must be >= 1");
            }
            this.maxTotal = maxTotal;
            return this;
        }

        /**
         * Maximum connections per route (per ClickHouse host).
         * Must not exceed {@link #maxTotal}.
         *
         * @param maxPerRoute must be &gt;= 1
         * @return this builder
         */
        public Builder maxPerRoute(int maxPerRoute) {
            if (maxPerRoute < 1) {
                throw new IllegalArgumentException("maxPerRoute must be >= 1");
            }
            this.maxPerRoute = maxPerRoute;
            return this;
        }

        /**
         * TCP connect timeout.
         *
         * @param connectTimeout must not be null or negative
         * @return this builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null || connectTimeout.isNegative()) {
                throw new IllegalArgumentException("connectTimeout must be non-negative");
            }
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Socket read/write timeout.
         * Set generously for long-running {@code OPTIMIZE TABLE} calls.
         *
         * @param socketTimeout must not be null or negative
         * @return this builder
         */
        public Builder socketTimeout(Duration socketTimeout) {
            if (socketTimeout == null || socketTimeout.isNegative()) {
                throw new IllegalArgumentException("socketTimeout must be non-negative");
            }
            this.socketTimeout = socketTimeout;
            return this;
        }

        /**
         * Connections idle for longer than this are closed by the eviction thread.
         *
         * @param idleEvictAfter must not be null or negative
         * @return this builder
         */
        public Builder idleEvictAfter(Duration idleEvictAfter) {
            if (idleEvictAfter == null || idleEvictAfter.isNegative()) {
                throw new IllegalArgumentException("idleEvictAfter must be non-negative");
            }
            this.idleEvictAfter = idleEvictAfter;
            return this;
        }

        /**
         * Period between background idle-connection eviction sweeps.
         * Pass {@link Duration#ZERO} to disable the eviction thread entirely.
         *
         * @param evictorInterval must not be null or negative
         * @return this builder
         */
        public Builder evictorInterval(Duration evictorInterval) {
            if (evictorInterval == null || evictorInterval.isNegative()) {
                throw new IllegalArgumentException("evictorInterval must be non-negative");
            }
            this.evictorInterval = evictorInterval;
            return this;
        }

        /**
         * Period of inactivity after which a connection is validated before being
         * leased (avoids half-open socket errors on keep-alive connections).
         *
         * @param validateAfterInactivity must not be null or negative
         * @return this builder
         */
        public Builder validateAfterInactivity(Duration validateAfterInactivity) {
            if (validateAfterInactivity == null || validateAfterInactivity.isNegative()) {
                throw new IllegalArgumentException("validateAfterInactivity must be non-negative");
            }
            this.validateAfterInactivity = validateAfterInactivity;
            return this;
        }

        /** Builds the {@link ChConnectionPoolConfig}. */
        public ChConnectionPoolConfig build() {
            return new ChConnectionPoolConfig(this);
        }
    }
}
