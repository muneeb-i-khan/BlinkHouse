package io.blinkhouse.spring;

import io.blinkhouse.spring.config.EnableClickHouseRepositories;
import io.blinkhouse.spring.repository.ClickHouseRepository;
import io.blinkhouse.spring.fixture.PageView;
import io.blinkhouse.spring.fixture.PageViewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring Data repository SPI wires correctly end-to-end:
 * scanning, proxy creation, injection, and fragment dispatch.
 *
 * <p>Covers:
 * <ol>
 *   <li>{@code @EnableClickHouseRepositories} scans and registers
 *       {@link PageViewRepository} as a Spring bean.</li>
 *   <li>Spring Data creates a JDK proxy backed by {@code SimpleClickHouseRepository}.</li>
 *   <li>The proxy is injectable via {@code @Autowired}.</li>
 *   <li>A custom fragment ({@code PageViewRepositoryImpl}) is discovered by convention
 *       and its methods are dispatched correctly through the proxy.</li>
 * </ol>
 *
 * <p>No ClickHouse container is required — no queries are executed against a server.
 */
@SpringJUnitConfig(RepositorySpiWiringIT.TestConfig.class)
class RepositorySpiWiringIT {

    @Configuration
    @EnableClickHouseRepositories(basePackages = "io.blinkhouse.spring.fixture")
    static class TestConfig {

        /**
         * No-op transaction manager — satisfies Spring Data's SPI requirement.
         * BlinkHouse has no real transactions.
         */
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() { return new Object(); }
                @Override
                protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
                @Override
                protected void doCommit(DefaultTransactionStatus s) {}
                @Override
                protected void doRollback(DefaultTransactionStatus s) {}
            };
        }
    }

    @Autowired
    PageViewRepository pageViewRepository;

    @Test
    void repositoryProxyIsInjected() {
        assertThat(pageViewRepository).isNotNull();
    }

    @Test
    void proxyImplementsRepositoryInterface() {
        assertThat(pageViewRepository).isInstanceOf(ClickHouseRepository.class);
    }

    @Test
    void customFragmentMethodIsDispatchedThroughProxy() {
        List<PageView> rows = pageViewRepository.findHardcoded();

        assertThat(rows)
                .hasSize(2)
                .extracting(PageView::country)
                .containsExactly("IN", "US");
    }
}
