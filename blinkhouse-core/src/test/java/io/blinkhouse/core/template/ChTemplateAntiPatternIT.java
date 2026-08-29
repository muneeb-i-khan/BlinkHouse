package io.blinkhouse.core.template;

import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChTable;
import io.blinkhouse.core.testcontainers.ClickHouseContainerExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that single-row insert increments the anti-pattern counter (P2 / R-1).
 */
@Testcontainers
class ChTemplateAntiPatternIT {

    @Container
    static final GenericContainer<?> CH = ClickHouseContainerExtension.INSTANCE;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @ChTable(name = "ap_test", orderBy = "id")
    record Event(
            @ChColumn(type = "UInt64") long id,
            @ChColumn(type = "UUID")  UUID traceId
    ) {}

    @BeforeAll
    static void createTable() throws Exception {
        execute("""
                CREATE TABLE IF NOT EXISTS ap_test (
                    id       UInt64,
                    trace_id UUID
                ) ENGINE = MergeTree() ORDER BY id
                """);
        execute("TRUNCATE TABLE ap_test");
    }

    @Test
    void singleRowInsertIncrementsCounter() {
        ChTemplate template = ChTemplate.builder()
                .url("http://" + ClickHouseContainerExtension.host()
                        + ":" + ClickHouseContainerExtension.httpPort())
                .credentials(ClickHouseContainerExtension.USER, ClickHouseContainerExtension.PASSWORD)
                .database(ClickHouseContainerExtension.DATABASE)
                .build();

        // @ChTable has no database set → qualifiedName is just `ap_test`
        String qualifiedName = "`ap_test`";

        assertThat(template.singleRowInsertCount(qualifiedName)).isZero();

        template.insertSingleRow(new Event(1L, UUID.randomUUID()));
        assertThat(template.singleRowInsertCount(qualifiedName)).isEqualTo(1);

        template.insertSingleRow(new Event(2L, UUID.randomUUID()));
        assertThat(template.singleRowInsertCount(qualifiedName)).isEqualTo(2);
    }

    @Test
    void bulkInsertDoesNotIncrementSingleRowCounter() {
        ChTemplate template = ChTemplate.builder()
                .url("http://" + ClickHouseContainerExtension.host()
                        + ":" + ClickHouseContainerExtension.httpPort())
                .credentials(ClickHouseContainerExtension.USER, ClickHouseContainerExtension.PASSWORD)
                .database(ClickHouseContainerExtension.DATABASE)
                .build();

        String qualifiedName = "`ap_test`";
        long before = template.singleRowInsertCount(qualifiedName);

        template.insert(Event.class,
                java.util.List.of(
                        new Event(100L, UUID.randomUUID()),
                        new Event(101L, UUID.randomUUID())
                ));

        assertThat(template.singleRowInsertCount(qualifiedName)).isEqualTo(before);
    }

    private static void execute(String sql) throws Exception {
        String url = ClickHouseContainerExtension.baseUrl()
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("SQL failed [" + resp.statusCode() + "]: " + resp.body());
        }
    }
}
