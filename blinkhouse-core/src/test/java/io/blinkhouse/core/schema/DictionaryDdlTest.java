package io.blinkhouse.core.schema;

import io.blinkhouse.core.annotation.ChDictionary;
import io.blinkhouse.core.annotation.ChDictionaryKey;
import io.blinkhouse.core.metadata.DictionaryMetadata;
import io.blinkhouse.core.metadata.DictionaryMetadataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for dictionary DDL generation.
 */
class DictionaryDdlTest {

    private final DdlGenerator ddl = new DefaultDdlGenerator();

    @ChDictionary(
        name = "product_dict",
        sourceType = ChDictionary.SourceType.CLICKHOUSE,
        sourceTable = "products",
        layout = ChDictionary.Layout.FLAT,
        lifetimeSeconds = 3600,
        lifetimeMaxSeconds = 3600
    )
    static class ProductDict {
        @ChDictionaryKey
        long productId;
        String name;
        double price;
    }

    @ChDictionary(
        name = "geo_dict",
        database = "lookup",
        sourceType = ChDictionary.SourceType.CLICKHOUSE,
        sourceTable = "geo_regions",
        sourceWhere = "active = 1",
        layout = ChDictionary.Layout.HASHED,
        lifetimeSeconds = 60,
        lifetimeMaxSeconds = 300,
        onCluster = "prod_cluster"
    )
    static class GeoDict {
        @ChDictionaryKey
        long regionId;
        String regionName;
    }

    @ChDictionary(
        name = "no_key_dict",
        sourceTable = "t"
    )
    static class NoKeyDict {
        String value;
    }

    @Test
    void createDictionaryFlatLayout() {
        DictionaryMetadata meta = DictionaryMetadataFactory.resolve(ProductDict.class);
        String ddlStr = ddl.createDictionary(meta, true);

        assertThat(ddlStr).startsWith("CREATE DICTIONARY IF NOT EXISTS `product_dict`");
        assertThat(ddlStr).contains("`productId` UInt64");
        assertThat(ddlStr).contains("`name` String");
        assertThat(ddlStr).contains("`price` Float64");
        assertThat(ddlStr).contains("PRIMARY KEY `productId`");
        assertThat(ddlStr).contains("SOURCE(CLICKHOUSE(TABLE 'products'))");
        assertThat(ddlStr).contains("LAYOUT(FLAT())");
        assertThat(ddlStr).contains("LIFETIME(3600)");
    }

    @Test
    void createDictionaryHashedWithDbAndCluster() {
        DictionaryMetadata meta = DictionaryMetadataFactory.resolve(GeoDict.class);
        String ddlStr = ddl.createDictionary(meta, false);

        assertThat(ddlStr).startsWith("CREATE DICTIONARY `lookup`.`geo_dict`");
        assertThat(ddlStr).doesNotContain("IF NOT EXISTS");
        assertThat(ddlStr).contains("ON CLUSTER `prod_cluster`");
        assertThat(ddlStr).contains("SOURCE(CLICKHOUSE(TABLE 'geo_regions' WHERE 'active = 1'))");
        assertThat(ddlStr).contains("LAYOUT(HASHED())");
        assertThat(ddlStr).contains("LIFETIME(MIN 60 MAX 300)");
    }

    @Test
    void missingKeyAnnotationThrows() {
        assertThatThrownBy(() -> DictionaryMetadataFactory.resolve(NoKeyDict.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("@ChDictionaryKey");
    }

    @Test
    void missingAnnotationThrows() {
        assertThatThrownBy(() -> DictionaryMetadataFactory.resolve(String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("@ChDictionary");
    }

    @Test
    void qualifiedNameWithDb() {
        DictionaryMetadata meta = DictionaryMetadataFactory.resolve(GeoDict.class);
        assertThat(meta.getQualifiedName()).isEqualTo("`lookup`.`geo_dict`");
    }

    @Test
    void qualifiedNameWithoutDb() {
        DictionaryMetadata meta = DictionaryMetadataFactory.resolve(ProductDict.class);
        assertThat(meta.getQualifiedName()).isEqualTo("`product_dict`");
    }
}
