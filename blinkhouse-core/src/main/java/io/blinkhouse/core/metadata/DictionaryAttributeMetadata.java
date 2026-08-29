package io.blinkhouse.core.metadata;

/**
 * Describes a single attribute (column) in a ClickHouse dictionary.
 */
public final class DictionaryAttributeMetadata {

    private final String name;
    private final String chTypeName;
    private final boolean isKey;
    private final String nullValue;

    public DictionaryAttributeMetadata(String name, String chTypeName, boolean isKey, String nullValue) {
        this.name = name;
        this.chTypeName = chTypeName;
        this.isKey = isKey;
        this.nullValue = nullValue == null ? "" : nullValue;
    }

    /** ClickHouse attribute name. */
    public String getName() {
        return name;
    }

    /** ClickHouse type string, e.g. {@code UInt64} or {@code String}. */
    public String getChTypeName() {
        return chTypeName;
    }

    /** Whether this attribute is the dictionary key. */
    public boolean isKey() {
        return isKey;
    }

    /** Default null value for missing lookups (empty string = no explicit null_value). */
    public String getNullValue() {
        return nullValue;
    }
}
