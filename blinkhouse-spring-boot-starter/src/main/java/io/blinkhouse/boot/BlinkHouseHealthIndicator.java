package io.blinkhouse.boot;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.template.ChTemplate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.List;

/**
 * Actuator {@link HealthIndicator} for the ClickHouse connection.
 *
 * <p>Issues a {@code SELECT version()} query. On success, reports {@code UP}
 * with the server version. On failure, reports {@code DOWN} with the error.
 */
public final class BlinkHouseHealthIndicator implements HealthIndicator {

    private final ChTemplate template;

    /**
     * Constructs the health indicator.
     *
     * @param template the ChTemplate to use for the health check query
     */
    public BlinkHouseHealthIndicator(ChTemplate template) {
        this.template = template;
    }

    @Override
    public Health health() {
        try {
            List<String> result = template.queryForList(String.class, "SELECT version()");
            String version = result.isEmpty() ? "unknown" : result.get(0);
            return Health.up().withDetail("version", version).build();
        } catch (ChException ex) {
            return Health.down(ex).withDetail("error", ex.getMessage()).build();
        }
    }
}
