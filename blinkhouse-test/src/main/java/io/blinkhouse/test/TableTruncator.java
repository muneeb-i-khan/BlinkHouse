package io.blinkhouse.test;

import io.blinkhouse.core.exception.ChException;
import io.blinkhouse.core.template.ChTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * Utility for truncating ClickHouse tables between tests.
 *
 * <p>Typical use in a {@code @BlinkHouseTest} class:
 * <pre>{@code
 * @Autowired TableTruncator truncator;
 *
 * @BeforeEach
 * void clean() {
 *     truncator.truncate("page_views", "events");
 * }
 * }</pre>
 */
public final class TableTruncator {

    private final ChTemplate template;

    /**
     * Constructs a truncator backed by the given template.
     *
     * @param template the ChTemplate to issue TRUNCATE statements through
     */
    public TableTruncator(ChTemplate template) {
        this.template = template;
    }

    /**
     * Truncates the given tables.
     *
     * <p>Each table is truncated with {@code TRUNCATE TABLE IF EXISTS <name>}.
     *
     * @param tableNames the table names to truncate (unquoted, plain identifiers)
     * @throws ChException on ClickHouse error
     */
    public void truncate(String... tableNames) throws ChException {
        truncate(Arrays.asList(tableNames));
    }

    /**
     * Truncates the given tables.
     *
     * @param tableNames the table names to truncate
     * @throws ChException on ClickHouse error
     */
    public void truncate(List<String> tableNames) throws ChException {
        for (String table : tableNames) {
            template.queryForList(String.class, "TRUNCATE TABLE IF EXISTS `" + table + "`");
        }
    }
}
