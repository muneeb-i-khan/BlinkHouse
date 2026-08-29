package io.blinkhouse.core.type;

import io.blinkhouse.core.type.handler.ArrayArrayStringHandler;
import io.blinkhouse.core.type.handler.DateTime64Handler;
import io.blinkhouse.core.type.handler.Decimal128Handler;
import io.blinkhouse.core.type.handler.IPv6Handler;
import io.blinkhouse.core.type.handler.Int256Handler;
import io.blinkhouse.core.type.handler.LowCardinalityNullableStringHandler;
import io.blinkhouse.core.type.handler.MapStringArrayUInt32Handler;
import io.blinkhouse.core.type.handler.TupleStringUInt8Handler;
import io.blinkhouse.core.type.handler.UInt64Handler;
import io.blinkhouse.core.type.handler.UuidHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry mapping ClickHouse type names to their {@link TypeHandler} implementations.
 *
 * <p>The registry is pre-loaded with all built-in handlers. Users can register additional
 * handlers at higher priority to override built-ins (priority 100 by convention).
 * Lookup is by exact ClickHouse type string; the metadata resolver calls this after
 * resolving the type string from annotations or Java-type inference.
 */
public final class TypeRegistry {

    private final Map<String, TypeHandler<?>> byChTypeName = new LinkedHashMap<>();

    private TypeRegistry() {}

    /** Returns a registry pre-loaded with all built-in type handlers. */
    public static TypeRegistry withDefaults() {
        TypeRegistry registry = new TypeRegistry();
        registry.register(new UInt64Handler());
        registry.register(new Int256Handler());
        registry.register(new Decimal128Handler(9));
        registry.register(new DateTime64Handler(9, java.time.ZoneId.of("UTC")));
        registry.register(new LowCardinalityNullableStringHandler());
        registry.register(new ArrayArrayStringHandler());
        registry.register(new MapStringArrayUInt32Handler());
        registry.register(new TupleStringUInt8Handler());
        registry.register(new UuidHandler());
        registry.register(new IPv6Handler());
        return registry;
    }

    /** Registers a handler, keyed by its {@link TypeHandler#clickHouseTypeName()}. */
    public void register(TypeHandler<?> handler) {
        byChTypeName.put(handler.clickHouseTypeName(), handler);
    }

    /**
     * Looks up a handler by exact ClickHouse type name.
     *
     * @throws IllegalArgumentException if no handler is registered for the type
     */
    @SuppressWarnings("unchecked")
    public <J> TypeHandler<J> lookup(String chTypeName) {
        TypeHandler<?> handler = byChTypeName.get(chTypeName);
        if (handler == null) {
            throw new IllegalArgumentException(
                    "No TypeHandler registered for ClickHouse type: '" + chTypeName + "'. "
                    + "Register a custom TypeHandler or use @ChColumn(type=\"...\") to specify an explicit type.");
        }
        return (TypeHandler<J>) handler;
    }

    /** Returns {@code true} if a handler is registered for the given type name. */
    public boolean isRegistered(String chTypeName) {
        return byChTypeName.containsKey(chTypeName);
    }
}
