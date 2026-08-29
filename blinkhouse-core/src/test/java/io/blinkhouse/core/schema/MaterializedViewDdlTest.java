package io.blinkhouse.core.schema;

import io.blinkhouse.core.annotation.ChMaterializedView;
import io.blinkhouse.core.metadata.MaterializedViewMetadata;
import io.blinkhouse.core.metadata.MaterializedViewMetadataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for materialized view DDL generation.
 */
class MaterializedViewDdlTest {

    private final DdlGenerator ddl = new DefaultDdlGenerator();

    @ChMaterializedView(
        name = "daily_sales_mv",
        targetTable = "daily_sales",
        selectSql = "SELECT toDate(ts) AS day, sum(amount) AS total FROM sales GROUP BY day"
    )
    static class DailySalesMv {}

    @ChMaterializedView(
        name = "hourly_events_mv",
        database = "analytics",
        selectSql = "SELECT toStartOfHour(ts) AS hr, count() AS cnt FROM events GROUP BY hr",
        populate = true,
        onCluster = "prod_cluster"
    )
    static class HourlyEventsMv {}

    @ChMaterializedView(
        selectSql = "SELECT 1"
    )
    static class DefaultNameMv {}

    @Test
    void createMaterializedViewWithTarget() {
        MaterializedViewMetadata mv = MaterializedViewMetadataFactory.resolve(DailySalesMv.class);
        String ddlStr = ddl.createMaterializedView(mv, true);

        assertThat(ddlStr).startsWith("CREATE MATERIALIZED VIEW IF NOT EXISTS `daily_sales_mv`");
        assertThat(ddlStr).contains("TO daily_sales");
        assertThat(ddlStr).contains("SELECT toDate(ts)");
        assertThat(ddlStr).doesNotContain("POPULATE");
        assertThat(ddlStr).doesNotContain("ON CLUSTER");
    }

    @Test
    void createMaterializedViewWithPopulateAndCluster() {
        MaterializedViewMetadata mv = MaterializedViewMetadataFactory.resolve(HourlyEventsMv.class);
        String ddlStr = ddl.createMaterializedView(mv, false);

        assertThat(ddlStr).startsWith("CREATE MATERIALIZED VIEW `analytics`.`hourly_events_mv`");
        assertThat(ddlStr).doesNotContain("IF NOT EXISTS");
        assertThat(ddlStr).contains("ON CLUSTER `prod_cluster`");
        assertThat(ddlStr).contains("POPULATE");
        assertThat(ddlStr).contains("SELECT toStartOfHour");
    }

    @Test
    void createMaterializedViewNoTarget_producesNoToClause() {
        // When targetTable is empty, no TO clause — ClickHouse creates an implicit storage table
        MaterializedViewMetadata mv = new MaterializedViewMetadata(
            "", "implicit_mv", "", "SELECT 1", false, java.util.Optional.empty());
        String ddlStr = ddl.createMaterializedView(mv, false);

        assertThat(ddlStr).doesNotContain(" TO ");
        assertThat(ddlStr).contains("SELECT 1");
    }

    @Test
    void defaultNameDerivedFromClassName() {
        MaterializedViewMetadata mv = MaterializedViewMetadataFactory.resolve(DefaultNameMv.class);
        // "DefaultNameMv" → "default_name_mv"
        assertThat(mv.getName()).isEqualTo("default_name_mv");
    }

    @Test
    void missingAnnotationThrows() {
        assertThatThrownBy(() -> MaterializedViewMetadataFactory.resolve(String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("@ChMaterializedView");
    }

    @Test
    void blankSelectSqlThrows() {
        assertThatThrownBy(() -> new MaterializedViewMetadata(
                "", "mv", "", "", false, java.util.Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
