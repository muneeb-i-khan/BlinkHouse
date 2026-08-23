package io.blinkhouse.spring.config;

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

import java.lang.annotation.Annotation;

/**
 * Processes {@link EnableClickHouseRepositories} and registers repository bean
 * definitions into the application context.
 */
class ClickHouseRepositoriesRegistrar extends RepositoryBeanDefinitionRegistrarSupport {

    @Override
    protected Class<? extends Annotation> getAnnotation() {
        return EnableClickHouseRepositories.class;
    }

    @Override
    protected RepositoryConfigurationExtension getExtension() {
        return new ClickHouseRepositoryConfigurationExtension();
    }
}
