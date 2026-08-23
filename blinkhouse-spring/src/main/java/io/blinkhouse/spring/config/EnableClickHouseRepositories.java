package io.blinkhouse.spring.config;

import io.blinkhouse.spring.repository.ClickHouseRepository;
import io.blinkhouse.spring.repository.ClickHouseRepositoryFactoryBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.config.DefaultRepositoryBaseClass;
import org.springframework.data.repository.query.QueryLookupStrategy;

import java.lang.annotation.*;

/**
 * Activates BlinkHouse repository scanning on the annotated {@code @Configuration} class.
 *
 * <p>Usage:
 * <pre>{@code
 * @Configuration
 * @EnableClickHouseRepositories(basePackages = "com.example.repositories")
 * public class ClickHouseConfig { }
 * }</pre>
 *
 * <p>Mirrors the convention established by {@code @EnableJpaRepositories} and
 * {@code @EnableMongoRepositories} — one annotation, zero XML.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(ClickHouseRepositoriesRegistrar.class)
public @interface EnableClickHouseRepositories {

    /** Alias for {@link #basePackages()}. */
    String[] value() default {};

    /** Base packages to scan for repository interfaces. */
    String[] basePackages() default {};

    /** Type-safe alternative to {@link #basePackages()}. */
    Class<?>[] basePackageClasses() default {};

    /** Filters to include specific components during scanning. */
    ComponentScan.Filter[] includeFilters() default {};

    /** Filters to exclude components during scanning. */
    ComponentScan.Filter[] excludeFilters() default {};

    /** Repository interface to use as the base. */
    Class<?> repositoryBaseClass() default DefaultRepositoryBaseClass.class;

    /** Factory bean class — always {@link ClickHouseRepositoryFactoryBean}. */
    Class<?> repositoryFactoryBeanClass() default ClickHouseRepositoryFactoryBean.class;

    /** Query lookup strategy. NONE in Spike C; CREATE_IF_NOT_FOUND from Phase 1. */
    QueryLookupStrategy.Key queryLookupStrategy() default QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND;

    /** Named query location. Unused in Spike C. */
    String namedQueriesLocation() default "";

    /**
     * Postfix appended to the repository interface name when looking for a custom
     * implementation class. Default matches Spring Data convention.
     */
    String repositoryImplementationPostfix() default "Impl";

    /**
     * The {@code @Configuration} bean name of the BlinkHouse transaction manager.
     * BlinkHouse has no real transactions — this is a placeholder for SPI compliance.
     */
    String transactionManagerRef() default "transactionManager";
}
