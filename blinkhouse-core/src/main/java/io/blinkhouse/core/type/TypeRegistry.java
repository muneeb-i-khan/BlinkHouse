package io.blinkhouse.core.type;

import io.blinkhouse.core.type.handler.ArrayArrayStringHandler;
import io.blinkhouse.core.type.handler.DateTime64Handler;
import io.blinkhouse.core.type.handler.Decimal128Handler;
import io.blinkhouse.core.type.handler.IPv6Handler;
import io.blinkhouse.core.type.handler.Int256Handler;
import io.blinkhouse.core.type.handler.Int32Handler;
import io.blinkhouse.core.type.handler.LowCardinalityNullableStringHandler;
import io.blinkhouse.core.type.handler.MapStringArrayUInt32Handler;
import io.blinkhouse.core.type.handler.StringHandler;
import io.blinkhouse.core.type.handler.TupleStringUInt8Handler;
import io.blinkhouse.core.type.handler.UInt32Handler;
import io.blinkhouse.core.type.handler.UInt64Handler;
import io.blinkhouse.core.type.handler.UuidHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link TypeHandler} implementations, keyed by ClickHouse type name.
 *
 * <p>Handlers are looked up by exact ClickHouse type name match first.
 * Use {@link #withDefaults()} to create a registry populated with all built-in handlers.
 */
public final class TypeRegistry {

    private final LinkedHashMap<String, TypeHandler<?>> byChTypeName = new LinkedHashMap<>();
    private final LinkedHashMap<Class<?>, TypeHandler<?>> byJavaType = new LinkedHashMap<>();

    /**
     * Creates a new registry pre-populated with all built-in type handlers.
     *
     * @return a new registry with defaults registered
     */
    public static TypeRegistry withDefaults() {
        TypeRegistry r = new TypeRegistry();
        r.register(new StringHandler());
        r.register(new UInt32Handler());
        r.register(new UInt64Handler());
        r.register(new Int32Handler());
        r.register(new Int256Handler());
        r.register(new Decimal128Handler(9));
        r.register(new DateTime64Handler(9, java.time.ZoneId.of("UTC")));
        r.register(new LowCardinalityNullableStringHandler());
        r.register(new ArrayArrayStringHandler());
        r.register(new MapStringArrayUInt32Handler());
        r.register(new TupleStringUInt8Handler());
        r.register(new UuidHandler());
        r.register(new IPv6Handler());
        return r;
    }

    /**
     * Registers a {@link TypeHandler}.
     *
     * @param handler the handler to register; its {@link TypeHandler#clickHouseTypeName()}
     *                is used as the key
     */
    public void register(TypeHandler<?> handler) {
        byChTypeName.put(handler.clickHouseTypeName(), handler);
    }

    /**
     * Registers a handler that also serves as the default for a Java type.
     *
     * @param handler  the handler to register
     * @param javaType the Java type this handler handles by default
     */
    public void register(TypeHandler<?> handler, Class<?> javaType) {
        byChTypeName.put(handler.clickHouseTypeName(), handler);
        byJavaType.put(javaType, handler);
    }

    /**
     * Finds a handler by ClickHouse type name. Checks for an exact match first, then
     * falls back to a prefix match for parameterized types (e.g. {@code "DateTime64(3,'UTC')"}
     * will match the handler registered for {@code "DateTime64(9,'UTC')"} if there is a handler
     * whose key starts with {@code "DateTime64("}).
     *
     * @param chTypeName the ClickHouse type name, e.g. {@code "UInt64"}
     * @return the handler, or empty if none registered for that name
     */
    public Optional<TypeHandler<?>> find(String chTypeName) {
        TypeHandler<?> exact = byChTypeName.get(chTypeName);
        if (exact != null) {
            return Optional.of(exact);
        }
        int paren = chTypeName.indexOf('(');
        if (paren > 0) {
            String base = chTypeName.substring(0, paren + 1);
            for (Map.Entry<String, TypeHandler<?>> e : byChTypeName.entrySet()) {
                if (e.getKey().startsWith(base)) {
                    return Optional.of(e.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a handler by Java type fallback.
     *
     * @param javaType the Java class to look up
     * @return the handler, or empty if none registered for that Java type
     */
    public Optional<TypeHandler<?>> findByJavaType(Class<?> javaType) {
        TypeHandler<?> h = byJavaType.get(javaType);
        if (h != null) {
            return Optional.of(h);
        }
        for (Map.Entry<String, TypeHandler<?>> e : byChTypeName.entrySet()) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Returns an unmodifiable view of all registered handlers keyed by ClickHouse type name. */
    public Map<String, TypeHandler<?>> all() {
        return Map.copyOf(byChTypeName);
    }
}
