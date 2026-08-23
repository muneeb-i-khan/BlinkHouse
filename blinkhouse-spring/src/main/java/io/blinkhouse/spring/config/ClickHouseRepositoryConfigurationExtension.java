package io.blinkhouse.spring.config;

import io.blinkhouse.spring.repository.ClickHouseRepository;
import io.blinkhouse.spring.repository.ClickHouseRepositoryFactoryBean;
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;

import java.util.Collection;
import java.util.Collections;

/**
 * Tells Spring Data how to identify BlinkHouse repository interfaces and which
 * factory bean to use when instantiating them.
 */
public class ClickHouseRepositoryConfigurationExtension
        extends RepositoryConfigurationExtensionSupport {

    @Override
    public String getModuleName() {
        return "ClickHouse";
    }

    @Override
    public String getRepositoryFactoryBeanClassName() {
        return ClickHouseRepositoryFactoryBean.class.getName();
    }

    @Override
    protected String getModulePrefix() {
        return "clickhouse";
    }

    @Override
    protected Collection<Class<?>> getIdentifyingTypes() {
        return Collections.singleton(ClickHouseRepository.class);
    }
}
