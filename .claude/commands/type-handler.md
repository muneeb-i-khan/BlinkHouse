# ClickORM — Scaffold a custom TypeHandler

The user wants to implement a custom `TypeHandler<J, C>` to add support for a new Java↔ClickHouse type mapping. Follow this process.

## When to write a TypeHandler

Custom handlers are needed for:
- A Java type not covered by the built-in mappings (e.g. a domain value object, a third-party type like `Money`)
- An overriding mapping for an existing type (e.g. store `UUID` as `String` instead of native UUID byte order)
- A new ClickHouse type not yet supported (e.g. `JSON`, `Variant`, `Dynamic` if the version predates built-in support)

## Step 1 — Identify the type pair

From $ARGUMENTS, establish:
- **Java type `J`** — the Java class this handler reads/writes
- **ClickHouse type `C`** — which `ClickHouseType` record this maps to (e.g. `ClickHouseType.Primitive`, `ClickHouseType.Decimal`, a custom composite)

## Step 2 — Understand the SPI contract

```java
public interface TypeHandler<J, C extends ClickHouseType> {
    // Declares the ClickHouse type for DDL generation
    C chType(TypeContext ctx);

    // Returns true if this handler should handle the given Java/CH type pair
    boolean supports(Class<?> javaType, ClickHouseType chType);

    // Write one value to the RowBinary stream
    void write(ChOutputStream out, J value, C type) throws IOException;

    // Read one value from the RowBinary stream
    J read(ChInputStream in, C type) throws IOException;

    // Higher priority wins when multiple handlers match (user handlers: 100, built-ins: 0)
    default int priority() { return 0; }
}
```

**Critical invariants:**
- `write` and `read` must be exact inverses: `read(write(v)) == v` for all valid values `v`
- Do NOT use `ZoneId.systemDefault()` anywhere in a temporal handler — timezone comes from the column type
- For `UUID`: ClickHouse stores UUIDs with swapped 64-bit halves vs RFC 4122 — if writing a UUID handler, mirror `UuidHandler`'s byte swap
- For `FixedString`: right-pad with `\0` on write; strip trailing `\0` on read
- `ChOutputStream` / `ChInputStream` are the RowBinary wire format — use their typed helpers, not raw bytes, unless implementing a truly custom binary format

## Step 3 — Generate the handler skeleton

```java
public final class MyTypeHandler implements TypeHandler<MyJavaType, ClickHouseType.Primitive> {

    public static final MyTypeHandler INSTANCE = new MyTypeHandler();

    @Override
    public ClickHouseType.Primitive chType(TypeContext ctx) {
        return new ClickHouseType.Primitive("String");  // or whichever CH type
    }

    @Override
    public boolean supports(Class<?> javaType, ClickHouseType chType) {
        return MyJavaType.class.isAssignableFrom(javaType)
            && chType instanceof ClickHouseType.Primitive p
            && p.name().equals("String");
    }

    @Override
    public void write(ChOutputStream out, MyJavaType value, ClickHouseType.Primitive type)
            throws IOException {
        // e.g. out.writeString(value.toString());
    }

    @Override
    public MyJavaType read(ChInputStream in, ClickHouseType.Primitive type)
            throws IOException {
        // e.g. return MyJavaType.of(in.readString());
    }

    @Override
    public int priority() { return 100; }  // override built-ins
}
```

## Step 4 — Registration

There are two ways to register a handler:

**Option A — ServiceLoader (works without Spring):**
Create `src/main/resources/META-INF/services/io.clickorm.core.type.TypeHandler` and add the fully-qualified handler class name:
```
io.myapp.MyTypeHandler
```

**Option B — Spring bean (starter auto-detects it):**
```java
@Bean
public TypeHandler<MyJavaType, ?> myTypeHandler() {
    return MyTypeHandler.INSTANCE;
}
```

Both are supported simultaneously. Spring beans take precedence over ServiceLoader entries when the starter is on the classpath.

## Step 5 — Use it on a column

```java
public record MyEntity(
    @ChColumn(type = "String")   // explicit type override triggers your handler
    MyJavaType myField
) {}
```

Or register it for the Java type globally (no `@ChColumn(type=...)` needed):
If `supports(MyJavaType.class, anyChType)` returns true, the resolver picks it up automatically.

## Step 6 — Write a property-based round-trip test

The type-handler test suite in ClickORM uses jqwik for property-based testing. Model your test the same way:

```java
@Property
void roundTrip(@ForAll MyJavaType value) throws IOException {
    var out = new ChOutputStream(new ByteArrayOutputStream());
    var type = handler.chType(TypeContext.defaults());
    handler.write(out, value, type);
    var in = new ChInputStream(new ByteArrayInputStream(out.toByteArray()));
    assertThat(handler.read(in, type)).isEqualTo(value);
}
```

**Mandatory edge cases to test:**
- Null / empty values (if the type is used inside `Nullable(T)`)
- Boundary values (min/max of the integer range, epoch dates, etc.)
- Unicode for string-backed types
- Type-specific traps (timezone for temporal, byte order for UUID, null padding for FixedString)

## Step 7 — Remind about the three silent-corruption traps

Before finishing, remind the user:
1. **UUID byte order**: ClickHouse stores UUIDs with swapped halves. If this handler touches UUIDs in any way, mirror the swap from `UuidHandler`.
2. **DateTime64 timezone**: Never read from `ZoneId.systemDefault()`. The timezone is in `ClickHouseType.DateTime64.timezone()`.
3. **FixedString null padding**: Always strip trailing `\0` on read; always right-pad to `N` bytes on write.
