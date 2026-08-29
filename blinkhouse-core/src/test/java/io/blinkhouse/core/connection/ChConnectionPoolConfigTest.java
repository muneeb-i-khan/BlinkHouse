package io.blinkhouse.core.connection;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChConnectionPoolConfigTest {

    @Test
    void defaultsAreProductionSensible() {
        ChConnectionPoolConfig cfg = ChConnectionPoolConfig.defaults();
        assertEquals(200, cfg.maxTotal());
        assertEquals(50, cfg.maxPerRoute());
        assertEquals(Duration.ofSeconds(5), cfg.connectTimeout());
        assertEquals(Duration.ofSeconds(60), cfg.socketTimeout());
        assertEquals(Duration.ofSeconds(30), cfg.idleEvictAfter());
        assertEquals(Duration.ofSeconds(5), cfg.evictorInterval());
        assertEquals(Duration.ofSeconds(10), cfg.validateAfterInactivity());
    }

    @Test
    void builderOverridesAllFields() {
        ChConnectionPoolConfig cfg = ChConnectionPoolConfig.builder()
            .maxTotal(400)
            .maxPerRoute(100)
            .connectTimeout(Duration.ofSeconds(3))
            .socketTimeout(Duration.ofMinutes(2))
            .idleEvictAfter(Duration.ofSeconds(15))
            .evictorInterval(Duration.ofSeconds(2))
            .validateAfterInactivity(Duration.ofSeconds(5))
            .build();

        assertEquals(400, cfg.maxTotal());
        assertEquals(100, cfg.maxPerRoute());
        assertEquals(Duration.ofSeconds(3), cfg.connectTimeout());
        assertEquals(Duration.ofMinutes(2), cfg.socketTimeout());
        assertEquals(Duration.ofSeconds(15), cfg.idleEvictAfter());
        assertEquals(Duration.ofSeconds(2), cfg.evictorInterval());
        assertEquals(Duration.ofSeconds(5), cfg.validateAfterInactivity());
    }

    @Test
    void maxTotalLessThanOneThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ChConnectionPoolConfig.builder().maxTotal(0).build());
    }

    @Test
    void maxPerRouteLessThanOneThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ChConnectionPoolConfig.builder().maxPerRoute(0).build());
    }

    @Test
    void negativeConnectTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ChConnectionPoolConfig.builder()
                .connectTimeout(Duration.ofSeconds(-1))
                .build());
    }

    @Test
    void negativeSocketTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ChConnectionPoolConfig.builder()
                .socketTimeout(Duration.ofSeconds(-1))
                .build());
    }

    @Test
    void evictorIntervalZeroDisablesEviction() {
        ChConnectionPoolConfig cfg = ChConnectionPoolConfig.builder()
            .evictorInterval(Duration.ZERO)
            .build();
        assertEquals(Duration.ZERO, cfg.evictorInterval());
    }

    @Test
    void factoryCreatesClientWithoutThrowing() {
        ChConnectionPoolConfig cfg = ChConnectionPoolConfig.defaults();
        org.apache.hc.client5.http.impl.classic.CloseableHttpClient client =
            ChHttpClientFactory.create(cfg);
        try {
            client.close();
        } catch (Exception e) {
            throw new AssertionError("close() should not throw", e);
        }
    }
}
