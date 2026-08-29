package io.blinkhouse.core.write;

import io.blinkhouse.core.metadata.ColumnMetadata;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * Serialises entities into ClickHouse {@code RowBinary} format.
 *
 * <p>Wraps an {@link OutputStream} and writes rows by invoking each column's
 * {@link TypeHandler} in declaration order. The header-free {@code RowBinary}
 * format is used for writes (the server infers column positions from the
 * {@code INSERT} column list in the query string).
 *
 * @param <T> the entity type
 */
@SuppressWarnings("unchecked")
public final class RowBinaryWriter<T> {

    private final EntityMetadata<T> metadata;
    private final CountingOutputStream counter;
    private final ChOutputStream out;

    /**
     * Constructs a writer that wraps {@code target}.
     *
     * @param metadata the resolved entity metadata
     * @param target   the stream to write RowBinary bytes into
     */
    public RowBinaryWriter(EntityMetadata<T> metadata, OutputStream target) {
        this.metadata = metadata;
        this.counter = new CountingOutputStream(target);
        this.out = new ChOutputStream(counter);
    }

    /**
     * Serialises {@code entity} as one RowBinary row.
     *
     * @param entity the entity to write
     * @throws IOException on I/O error
     */
    public void writeRow(T entity) throws IOException {
        List<ColumnMetadata<T>> cols = metadata.getInsertableColumns();
        for (ColumnMetadata<T> col : cols) {
            Object value = col.getAccessor().get(entity);
            ((TypeHandler<Object>) col.getHandler()).write(out, value);
        }
    }

    /**
     * Serialises all entities in {@code rows}.
     *
     * @param rows the entities to write; must not be {@code null}
     * @throws IOException on I/O error
     */
    public void writeAll(Collection<T> rows) throws IOException {
        for (T row : rows) {
            writeRow(row);
        }
    }

    /**
     * Builds the {@code INSERT INTO ... FORMAT RowBinary} SQL string.
     *
     * @return the SQL statement for this entity's table
     */
    public String buildInsertSql() {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(metadata.getQualifiedName());
        sb.append(" (");
        List<ColumnMetadata<T>> cols = metadata.getInsertableColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('`').append(cols.get(i).getName()).append('`');
        }
        sb.append(") FORMAT RowBinary");
        return sb.toString();
    }

    /**
     * Returns the total number of bytes written to the underlying stream.
     *
     * @return byte count
     */
    public long getBytesWritten() {
        return counter.getCount();
    }

    /** Flushes the underlying stream. */
    public void flush() throws IOException {
        out.flush();
    }

    private static final class CountingOutputStream extends OutputStream {

        private final OutputStream delegate;
        private long count;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        long getCount() {
            return count;
        }
    }
}
