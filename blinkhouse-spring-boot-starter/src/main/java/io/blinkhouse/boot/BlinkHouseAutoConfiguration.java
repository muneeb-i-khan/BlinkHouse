package io.blinkhouse.boot;

import io.blinkhouse.core.observability.ChMetrics;
import io.blinkhouse.core.observability.ChTracer;
import io.blinkhouse.core.observability.NoopChMetrics;
import io.blinkhouse.core.observability.NoopChTracer;
import io.blinkhouse.core.observability.QueryIdGenerator;
import io.blinkhouse.core.schema.DefaultDdlGenerator;
import io.blinkhouse.core.schema.HttpSchemaIntrospector;
import io.blinkhouse.core.schema.SchemaManager;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.spring.config.EnableClickHouseRepositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * Spring Boot auto-configuration for BlinkHouse.
 *
 * <p>Wires the following beans when {@code clickhouse.url} is set:
 * <ul>
 *   <li>{@link TypeRegistry} — pre-populated with all built-in type handlers</li>
 *   <li>{@link QueryIdGenerator} — correlatable query ID generator</li>
 *   <li>{@link ChTemplate} — the central execution facade with observability wired in</li>
 *   <li>{@link SchemaManager} — schema lifecycle manager, runs before any BatchWriter</li>
 * </ul>
 *
 * <p>Repositories are auto-activated by {@link EnableClickHouseRepositories}
 * with component scanning from the application's root package.
 *
 * <p>Bean ordering: {@code chSchemaManager} is created before any user-defined
 * {@code BatchWriter} bean through the {@code @DependsOn("chTemplate")} chain,
 * preventing a race between table creation and the first insert.
 */
@AutoConfiguration(before = BlinkHouseMetricsAutoConfiguration.class)
@ConditionalOnClass(ChTemplate.class)
@EnableConfigurationProperties(BlinkHouseProperties.class)
@EnableClickHouseRepositories
public class BlinkHouseAutoConfiguration {

    /**
     * Registers the default {@link TypeRegistry} with all built-in handlers.
     *
     * @return a pre-populated type registry
     */
    @Bean
    @ConditionalOnMissingBean
    public TypeRegistry chTypeRegistry() {
        return TypeRegistry.withDefaults();
    }

    /**
     * Registers the {@link QueryIdGenerator}.
     *
     * @param properties the BlinkHouse properties (provides the app name)
     * @return a query ID generator
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryIdGenerator chQueryIdGenerator(BlinkHouseProperties properties) {
        String appName = properties.getAppName() != null ? properties.getAppName() : "app";
        return new QueryIdGenerator(appName);
    }

    /**
     * Registers the {@link ChTemplate} bean with observability injected.
     *
     * <p>{@code ChMetrics} and {@code ChTracer} are optional — if they are not yet
     * registered (because {@link BlinkHouseMetricsAutoConfiguration} runs after this),
     * they default to no-op implementations. The setter-injection pattern below
     * handles the case where Micrometer is present.
     *
     * @param properties       the BlinkHouse configuration properties
     * @param registry         the type registry
     * @param queryIdGenerator the query ID generator
     * @param metrics          optional ChMetrics bean (null if Micrometer absent)
     * @param tracer           optional ChTracer bean (null if tracing absent)
     * @return a configured ChTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public ChTemplate chTemplate(BlinkHouseProperties properties,
                                 TypeRegistry registry,
                                 QueryIdGenerator queryIdGenerator,
                                 @Autowired(required = false) ChMetrics metrics,
                                 @Autowired(required = false) ChTracer tracer) {
        return ChTemplate.builder(properties.buildBaseUrl())
            .registry(registry)
            .queryIdGenerator(queryIdGenerator)
            .metrics(metrics != null ? metrics : NoopChMetrics.INSTANCE)
            .tracer(tracer != null ? tracer : NoopChTracer.INSTANCE)
            .build();
    }

    /**
     * Registers the {@link SchemaManager} bean.
     *
     * <p>The schema manager runs before any {@code BatchWriter} (enforced by
     * {@code @DependsOn}) to prevent inserts racing table creation.
     *
     * @param properties the BlinkHouse configuration properties
     * @param template   the ChTemplate (provides the HTTP connection)
     * @return a configured SchemaManager
     */
    @Bean
    @ConditionalOnMissingBean
    @DependsOn("chTemplate")
    public SchemaManager chSchemaManager(BlinkHouseProperties properties, ChTemplate template) {
        BlinkHouseProperties.SchemaProperties sp = properties.getSchema();
        return new SchemaManager(
            sp.getMode(),
            sp.isAllowDestructive(),
            new HttpSchemaIntrospector(template.getBaseUrl()),
            new DefaultDdlGenerator(),
            template.getBaseUrl()
        );
    }
}
