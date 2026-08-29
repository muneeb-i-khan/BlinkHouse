package io.blinkhouse.spring.support;

import io.blinkhouse.core.exception.ChBackpressureException;
import io.blinkhouse.core.exception.ChBufferFullException;
import io.blinkhouse.core.exception.ChConnectionException;
import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.exception.ChMappingException;
import io.blinkhouse.core.exception.ChMemoryLimitException;
import io.blinkhouse.core.exception.ChSchemaException;
import io.blinkhouse.core.exception.ChSyntaxException;
import io.blinkhouse.core.exception.ChTimeoutException;
import io.blinkhouse.core.exception.ChTooManyPartsException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Translates {@link ChException} subtypes into Spring's {@link DataAccessException} hierarchy.
 *
 * <p>Callers that prefer to work with Spring's portable exception hierarchy (e.g. to plug
 * into existing Spring retry / exception-handling infrastructure) should wrap repository
 * calls or use this translator explicitly.
 */
public final class SpringChExceptionTranslator {

    private SpringChExceptionTranslator() {
    }

    /**
     * Translates a {@link ChException} into the nearest Spring {@link DataAccessException}.
     *
     * @param ex the BlinkHouse exception to translate
     * @return a Spring DataAccessException wrapping the original
     */
    public static DataAccessException translate(ChException ex) {
        if (ex instanceof ChTimeoutException) {
            return new QueryTimeoutException(ex.getMessage(), ex);
        }
        if (ex instanceof ChConnectionException || ex instanceof ChMemoryLimitException
                || ex instanceof ChTooManyPartsException) {
            return new TransientDataAccessResourceException(ex.getMessage(), ex);
        }
        if (ex instanceof ChBackpressureException || ex instanceof ChBufferFullException) {
            return new TransientDataAccessResourceException(ex.getMessage(), ex);
        }
        if (ex instanceof ChSyntaxException || ex instanceof ChMappingException) {
            return new InvalidDataAccessApiUsageException(ex.getMessage(), ex);
        }
        if (ex instanceof ChSchemaException) {
            return new InvalidDataAccessApiUsageException(ex.getMessage(), ex);
        }
        return new DataAccessResourceFailureException(ex.getMessage(), ex);
    }
}
