package io.blinkhouse.boot;

import io.blinkhouse.core.observability.ChMetrics;
import io.blinkhouse.core.observability.ChTracer;
import io.blinkhouse.core.observability.NoopChMetrics;
import io.blinkhouse.core.observability.NoopChTracer;
import io.blinkhouse.core.template.ChTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
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
 * <p>Registers the following beans conditionally:
 * <ul>
 *   <li>{@link BlinkHouseHealthIndicator} — when Actuator and a {@link ChTemplate} are present</li>
 *   <li>{@link MicrometerChMetrics} — when {@code micrometer-core} and a {@link MeterRegistry} are present</li>
 *   <li>{@link MicrometerChTracer} — when {@code micrometer-tracing} and a {@link Tracer} are present</li>
 * </ul>
 *
 * <p>Falls back to {@link NoopChMetrics} and {@link NoopChTracer} when the respective
 * libraries are absent, keeping {@code blinkhouse-core} free of Micrometer dependencies.
 */
@AutoConfiguration(after = BlinkHouseAutoConfiguration.class)
@ConditionalOnBean(ChTemplate.class)
public class BlinkHouseMetricsAutoConfiguration {

    /**
     * Registers the ClickHouse health indicator for Spring Boot Actuator.
     *
     * @param template the ChTemplate bean
     * @return the health indicator
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "clickHouseHealthIndicator")
    @ConditionalOnEnabledHealthIndicator("clickhouse")
    public HealthIndicator clickHouseHealthIndicator(ChTemplate template) {
        return new BlinkHouseHealthIndicator(template);
    }

    /**
     * Registers a Micrometer-backed {@link ChMetrics} when {@code MeterRegistry} is present.
     *
     * @param registry the Micrometer meter registry
     * @return a Micrometer metrics implementation
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(ChMetrics.class)
    public ChMetrics blinkHouseMetrics(MeterRegistry registry) {
        return new MicrometerChMetrics(registry);
    }

    /**
     * Registers a no-op {@link ChMetrics} when Micrometer is absent.
     *
     * @return the no-op implementation
     */
    @Bean
    @ConditionalOnMissingBean(ChMetrics.class)
    public ChMetrics blinkHouseMetricsNoop() {
        return NoopChMetrics.INSTANCE;
    }

    /**
     * Registers a Micrometer Tracing-backed {@link ChTracer} when a {@link Tracer} bean is present.
     *
     * @param tracer the Micrometer tracer
     * @return a Micrometer tracer implementation
     */
    @Bean
    @ConditionalOnClass(Tracer.class)
    @ConditionalOnBean(Tracer.class)
    @ConditionalOnMissingBean(ChTracer.class)
    public ChTracer blinkHouseTracer(Tracer tracer) {
        return new MicrometerChTracer(tracer);
    }

    /**
     * Registers a no-op {@link ChTracer} when Micrometer Tracing is absent.
     *
     * @return the no-op implementation
     */
    @Bean
    @ConditionalOnMissingBean(ChTracer.class)
    public ChTracer blinkHouseTracerNoop() {
        return NoopChTracer.INSTANCE;
    }
}
