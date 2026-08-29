package io.blinkhouse.example.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the BlinkHouse analytics reference application.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Zero-config BlinkHouse wiring via {@code application.yml}</li>
 *   <li>High-throughput event ingest through {@link io.blinkhouse.core.write.BatchWriter}</li>
 *   <li>{@code OPTIMIZE TABLE} after bulk import for immediate deduplication</li>
 *   <li>Prometheus metrics via Micrometer at {@code /actuator/prometheus}</li>
 * </ul>
 *
 * <p>Start a local ClickHouse instance, then run:
 * <pre>
 *   mvn spring-boot:run -pl examples/analytics-service
 * </pre>
 */
@SpringBootApplication
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
