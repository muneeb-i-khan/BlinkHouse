package io.blinkhouse.boot;

import io.blinkhouse.core.schema.DefaultDdlGenerator;
import io.blinkhouse.core.schema.HttpSchemaIntrospector;
import io.blinkhouse.core.schema.SchemaManager;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.spring.config.EnableClickHouseRepositories;
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
 *   <li>{@link ChTemplate} — the central execution facade</li>
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
@AutoConfiguration
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
     * Registers the {@link ChTemplate} bean.
     *
     * @param properties the BlinkHouse configuration properties
     * @param registry   the type registry
     * @return a configured ChTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public ChTemplate chTemplate(BlinkHouseProperties properties, TypeRegistry registry) {
        return ChTemplate.builder(properties.buildBaseUrl())
            .registry(registry)
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
