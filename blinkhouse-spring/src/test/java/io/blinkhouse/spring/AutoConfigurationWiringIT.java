package io.blinkhouse.spring;

import io.blinkhouse.core.schema.SchemaMode;
import io.blinkhouse.core.template.ChTemplate;
import io.blinkhouse.core.type.TypeRegistry;
import io.blinkhouse.spring.config.EnableClickHouseRepositories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a minimal Spring context with {@code @EnableClickHouseRepositories}
 * and a {@link ChTemplate} bean wires correctly — without a running ClickHouse server.
 *
 * <p>This proves the Spring integration layer (factory bean, query lookup strategy,
 * entity information) assembles without errors at context refresh.
 */
@SpringJUnitConfig(AutoConfigurationWiringIT.TestConfig.class)
class AutoConfigurationWiringIT {

    @Configuration
    @EnableClickHouseRepositories(basePackages = "io.blinkhouse.spring.fixture")
    static class TestConfig {

        @Bean
        ChTemplate chTemplate() {
            return ChTemplate.builder("http://localhost:8123/?user=default&password=&database=default")
                .registry(TypeRegistry.withDefaults())
                .build();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object t,
                        org.springframework.transaction.TransactionDefinition d) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus s) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus s) {
                }
            };
        }
    }

    @Autowired
    ChTemplate chTemplate;

    @Test
    void chTemplateBeanIsWired() {
        assertThat(chTemplate).isNotNull();
    }

    @Test
    void chTemplateBaseUrlIncludesCredentials() {
        assertThat(chTemplate.getBaseUrl()).contains("user=default");
    }
}
