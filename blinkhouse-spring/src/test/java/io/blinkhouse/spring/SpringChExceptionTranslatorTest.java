package io.blinkhouse.spring;

import io.blinkhouse.core.exception.ChBackpressureException;
import io.blinkhouse.core.exception.ChConnectionException;
import io.blinkhouse.core.exception.ChErrorCode;
import io.blinkhouse.core.exception.ChMappingException;
import io.blinkhouse.core.exception.ChSchemaException;
import io.blinkhouse.core.exception.ChSyntaxException;
import io.blinkhouse.core.exception.ChTimeoutException;
import io.blinkhouse.spring.support.SpringChExceptionTranslator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SpringChExceptionTranslator}.
 */
class SpringChExceptionTranslatorTest {

    @Test
    void timeoutExceptionMapsToQueryTimeoutException() {
        ChTimeoutException ex = new ChTimeoutException("timeout", ChErrorCode.TIMEOUT_EXCEEDED);
        DataAccessException dae = SpringChExceptionTranslator.translate(ex);
        assertThat(dae).isInstanceOf(QueryTimeoutException.class);
        assertThat(dae.getCause()).isSameAs(ex);
    }

    @Test
    void connectionExceptionMapsToTransient() {
        ChConnectionException ex = new ChConnectionException("conn", ChErrorCode.NETWORK_ERROR);
        DataAccessException dae = SpringChExceptionTranslator.translate(ex);
        assertThat(dae).isInstanceOf(TransientDataAccessResourceException.class);
    }

    @Test
    void backpressureExceptionMapsToTransient() {
        ChBackpressureException ex = new ChBackpressureException("bp");
        DataAccessException dae = SpringChExceptionTranslator.translate(ex);
        assertThat(dae).isInstanceOf(TransientDataAccessResourceException.class);
    }

    @Test
    void syntaxExceptionMapsToInvalidUsage() {
        ChSyntaxException ex = new ChSyntaxException("syntax error in query");
        DataAccessException dae = SpringChExceptionTranslator.translate(ex);
        assertThat(dae).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void mappingExceptionMapsToInvalidUsage() {
        ChMappingException ex = new ChMappingException("mapping");
        DataAccessException dae = SpringChExceptionTranslator.translate(ex);
        assertThat(dae).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void schemaExceptionMapsToInvalidUsage() {
        ChSchemaException ex = new ChSchemaException("schema");
        DataAccessException dae = SpringChExceptionTranslator.translate(ex);
        assertThat(dae).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }
}
