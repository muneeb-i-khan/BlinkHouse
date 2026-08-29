package io.blinkhouse.test;

import io.blinkhouse.boot.BlinkHouseAutoConfiguration;
import io.blinkhouse.boot.BlinkHouseMetricsAutoConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test slice annotation that boots only the BlinkHouse layer — no web server, no JPA,
 * no full Spring Boot application context.
 *
 * <p>The slice:
 * <ul>
 *   <li>Disables standard auto-configuration and enables only
 *       {@link BlinkHouseAutoConfiguration} and {@link BlinkHouseMetricsAutoConfiguration}.</li>
 *   <li>Provides {@code clickhouse.*} properties from {@code application-blinkhouse-test.yml}
 *       (on the test classpath) or from the {@code BH_CLICKHOUSE_IMAGE} environment variable.</li>
 *   <li>Targets the {@code blinkhouse-test} Spring profile, which should point
 *       {@code clickhouse.url} at the reused {@link BhTestContainer} singleton.</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @BlinkHouseTest
 * class PageViewRepositoryIT {
 *
 *     @Autowired PageViewRepository repository;
 *     @Autowired TableTruncator truncator;
 *
 *     @BeforeEach
 *     void setUp() throws ChException {
 *         truncator.truncate("page_views");
 *     }
 *
 *     @Test
 *     void insertAndCount() {
 *         repository.insertAll(fixtures());
 *         assertThat(repository.count()).isEqualTo(fixtures().size());
 *     }
 * }
 * }</pre>
 *
 * <p>Wire the URL with a {@code @TestConfiguration} or a
 * {@code application-blinkhouse-test.properties} file containing:
 * <pre>
 * clickhouse.url=http://localhost:8123
 * clickhouse.username=bh_test
 * clickhouse.password=bh_test
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@ImportAutoConfiguration({ BlinkHouseAutoConfiguration.class, BlinkHouseMetricsAutoConfiguration.class })
@ActiveProfiles("blinkhouse-test")
public @interface BlinkHouseTest {

    /**
     * Additional auto-configuration classes to import alongside the BlinkHouse slice.
     *
     * @return additional auto-configuration classes
     */
    Class<?>[] additionalAutoConfigurations() default {};
}
