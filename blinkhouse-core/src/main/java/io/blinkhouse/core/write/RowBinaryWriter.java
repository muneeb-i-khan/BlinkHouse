package io.blinkhouse.core.write;

import io.blinkhouse.core.metadata.ColumnMetadata;
import io.blinkhouse.core.metadata.EntityMetadata;
import io.blinkhouse.core.protocol.ChOutputStream;
import io.blinkhouse.core.type.TypeHandler;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * Serialises a collection of entity rows into ClickHouse's {@code RowBinary} wire format.
 *
 * <p><strong>Format:</strong> rows are concatenated with no header, no separators, and
 * no row count prefix. Each row is the sequential serialisation of its
 * {@link EntityMetadata#getInsertableColumns() insertable columns} via their
 * {@link TypeHandler#write} implementations.
 *
 * <p>This is intentionally different from {@code RowBinaryWithNamesAndTypes} (used for
 * reads). For writes, ClickHouse uses the column list from the INSERT statement itself
 * as the schema — the binary body is schema-free. The INSERT SQL is built by
 * {@link BatchWriter} and includes an explicit column list derived from
 * {@code insertableColumns}.
 *
 * <p>One instance per flush block. Create, write all rows, close to release the stream.
 * Not thread-safe — owned by a single flusher thread.
 *
 * @param <T> entity type
 */
public final class RowBinaryWriter<T> implements Closeable {

    private final EntityMetadata<T> metadata;
    private final ChOutputStream out;
    private long bytesWritten = 0;
    private final CountingOutputStream counter;

    /**
     * Wraps {@code outputStream} with a {@link ChOutputStream} and binds to the
     * entity metadata for this writer's lifetime.
     */
    public RowBinaryWriter(EntityMetadata<T> metadata, OutputStream outputStream) {
        this.metadata = metadata;
        this.counter = new CountingOutputStream(outputStream);
        this.out = new ChOutputStream(counter);
    }

    /**
     * Serialises a single row into the output stream.
     *
     * @throws IOException if the underlying stream fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeRow(T entity) throws IOException {
        for (ColumnMetadata<T> col : metadata.getInsertableColumns()) {
            Object value = col.getAccessor().get(entity);
            TypeHandler handler = col.getHandler();
            handler.write(out, value);
        }
    }

    /**
     * Convenience method — writes all rows in the collection.
     *
     * @throws IOException if the underlying stream fails
     */
    public void writeAll(Collection<? extends T> rows) throws IOException {
        for (T row : rows) {
            writeRow(row);
        }
        out.flush();
    }

    /**
     * Total bytes written so far (including any bytes not yet flushed to the
     * underlying stream). Updated after each {@link #writeRow} call.
     */
    public long getBytesWritten() {
        return counter.getCount();
    }

    /**
     * Builds the INSERT SQL for this entity's insertable columns.
     *
     * <p>Example: {@code INSERT INTO `db`.`tbl` (col_a, col_b, col_c) FORMAT RowBinary}
     */
    public String buildInsertSql() {
        List<ColumnMetadata<T>> insertable = metadata.getInsertableColumns();
        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(metadata.getQualifiedName())
                .append(" (");
        for (int i = 0; i < insertable.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('`').append(insertable.get(i).getName()).append('`');
        }
        sb.append(") FORMAT RowBinary");
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    /** Wraps an OutputStream to count bytes written. */
    private static final class CountingOutputStream extends OutputStream {

        private final OutputStream delegate;
        private long count = 0;

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
        public void write(byte[] b) throws IOException {
            delegate.write(b);
            count += b.length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        long getCount() {
            return count;
        }
    }
}
