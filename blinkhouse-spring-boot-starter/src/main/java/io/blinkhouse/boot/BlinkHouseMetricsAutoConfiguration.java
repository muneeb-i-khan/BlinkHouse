package io.blinkhouse.boot;

import io.blinkhouse.core.template.ChTemplate;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for BlinkHouse observability.
 *
 * <p>Registers the {@link BlinkHouseHealthIndicator} when:
 * <ul>
 *   <li>Spring Boot Actuator is on the classpath</li>
 *   <li>{@code management.health.clickhouse.enabled} is not set to {@code false}</li>
 *   <li>A {@link ChTemplate} bean is present</li>
 * </ul>
 *
 * <p>Micrometer metric wiring is left to Phase 6 (Observability).
 * This class provides the structural hook without requiring Micrometer on the classpath.
 */
@AutoConfiguration(after = BlinkHouseAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(ChTemplate.class)
public class BlinkHouseMetricsAutoConfiguration {

    /**
     * Registers the ClickHouse health indicator for Spring Boot Actuator.
     *
     * @param template the ChTemplate bean
     * @return the health indicator
     */
    @Bean
    @ConditionalOnMissingBean(name = "clickHouseHealthIndicator")
    @ConditionalOnEnabledHealthIndicator("clickhouse")
    public HealthIndicator clickHouseHealthIndicator(ChTemplate template) {
        return new BlinkHouseHealthIndicator(template);
    }
}
