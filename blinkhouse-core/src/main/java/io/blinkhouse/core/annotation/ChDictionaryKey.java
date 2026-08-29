package io.blinkhouse.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the key column(s) in a {@link ChDictionary}-annotated class.
 *
 * <p>For FLAT/HASHED/CACHE layouts the key must be a single numeric field.
 * For COMPLEX_KEY_HASHED/COMPLEX_KEY_CACHE layouts multiple fields may be annotated.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ChDictionaryKey {
}
