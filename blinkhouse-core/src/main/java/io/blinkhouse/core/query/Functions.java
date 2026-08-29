package io.blinkhouse.core.query;

import io.blinkhouse.core.query.ast.Expression;
import io.blinkhouse.core.query.ast.FunctionCall;

/**
 * Static factory for common ClickHouse functions.
 *
 * <p>All methods return {@link FunctionCall} or {@link Expression} nodes —
 * never raw SQL strings. Callers can compose these with the rest of the AST.
 *
 * <p>Functions are grouped by category:
 * <ul>
 *   <li>Aggregates
 *   <li>Date/time
 *   <li>String
 *   <li>Array
 *   <li>Math
 *   <li>Type conversion
 *   <li>Conditional
 *   <li>Networking / UUID
 * </ul>
 */
public final class Functions {

    private Functions() {
    }

    // ── Aggregates ─────────────────────────────────────────────────────────────

    /**
     * {@code count()} — total row count.
     *
     * @return a count expression
     */
    public static FunctionCall count() {
        return FunctionCall.of("count");
    }

    /**
     * {@code count(expr)} — non-null row count.
     *
     * @param expr the expression to count
     * @return a count expression
     */
    public static FunctionCall count(Expression expr) {
        return FunctionCall.of("count", expr);
    }

    /**
     * {@code uniq(expr)} — approximate distinct count.
     *
     * @param expr the expression to count
     * @return a uniq expression
     */
    public static FunctionCall uniq(Expression expr) {
        return FunctionCall.of("uniq", expr);
    }

    /**
     * {@code uniqExact(expr)} — exact distinct count.
     *
     * @param expr the expression to count
     * @return a uniqExact expression
     */
    public static FunctionCall uniqExact(Expression expr) {
        return FunctionCall.of("uniqExact", expr);
    }

    /**
     * {@code sum(expr)} — sum.
     *
     * @param expr the expression to sum
     * @return a sum expression
     */
    public static FunctionCall sum(Expression expr) {
        return FunctionCall.of("sum", expr);
    }

    /**
     * {@code avg(expr)} — arithmetic mean.
     *
     * @param expr the expression to average
     * @return an avg expression
     */
    public static FunctionCall avg(Expression expr) {
        return FunctionCall.of("avg", expr);
    }

    /**
     * {@code min(expr)} — minimum value.
     *
     * @param expr the expression
     * @return a min expression
     */
    public static FunctionCall min(Expression expr) {
        return FunctionCall.of("min", expr);
    }

    /**
     * {@code max(expr)} — maximum value.
     *
     * @param expr the expression
     * @return a max expression
     */
    public static FunctionCall max(Expression expr) {
        return FunctionCall.of("max", expr);
    }

    /**
     * {@code groupArray(expr)} — collects all values into an array.
     *
     * @param expr the expression
     * @return a groupArray expression
     */
    public static FunctionCall groupArray(Expression expr) {
        return FunctionCall.of("groupArray", expr);
    }

    /**
     * {@code groupArray(n)(expr)} — top-n variant.
     *
     * @param expr the expression
     * @param n    maximum array size
     * @return a groupArray(n)(expr) expression rendered as a raw parametric call
     */
    public static FunctionCall groupArrayN(Expression expr, long n) {
        return FunctionCall.of("groupArray(" + n + ")", expr);
    }

    /**
     * {@code quantile(level)(expr)} — approximate quantile.
     *
     * @param level the quantile level (0..1)
     * @param expr  the expression
     * @return a quantile expression
     */
    public static FunctionCall quantile(double level, Expression expr) {
        return FunctionCall.of("quantile(" + level + ")", expr);
    }

    /**
     * {@code countIf(expr, cond)} — conditional count.
     *
     * @param expr      the expression to count
     * @param condition the filter condition
     * @return a countIf expression
     */
    public static FunctionCall countIf(Expression expr, Expression condition) {
        return FunctionCall.of("countIf", expr, condition);
    }

    /**
     * {@code sumIf(expr, cond)} — conditional sum.
     *
     * @param expr      the expression to sum
     * @param condition the filter condition
     * @return a sumIf expression
     */
    public static FunctionCall sumIf(Expression expr, Expression condition) {
        return FunctionCall.of("sumIf", expr, condition);
    }

    /**
     * {@code anyLast(expr)} — last non-null value in insertion order.
     *
     * @param expr the expression
     * @return an anyLast expression
     */
    public static FunctionCall anyLast(Expression expr) {
        return FunctionCall.of("anyLast", expr);
    }

    // ── Date / time ────────────────────────────────────────────────────────────

    /**
     * {@code toStartOfMinute(expr)}.
     *
     * @param expr the datetime expression
     * @return a toStartOfMinute expression
     */
    public static FunctionCall toStartOfMinute(Expression expr) {
        return FunctionCall.of("toStartOfMinute", expr);
    }

    /**
     * {@code toStartOfHour(expr)}.
     *
     * @param expr the datetime expression
     * @return a toStartOfHour expression
     */
    public static FunctionCall toStartOfHour(Expression expr) {
        return FunctionCall.of("toStartOfHour", expr);
    }

    /**
     * {@code toStartOfDay(expr)}.
     *
     * @param expr the datetime expression
     * @return a toStartOfDay expression
     */
    public static FunctionCall toStartOfDay(Expression expr) {
        return FunctionCall.of("toStartOfDay", expr);
    }

    /**
     * {@code toStartOfWeek(expr)}.
     *
     * @param expr the datetime expression
     * @return a toStartOfWeek expression
     */
    public static FunctionCall toStartOfWeek(Expression expr) {
        return FunctionCall.of("toStartOfWeek", expr);
    }

    /**
     * {@code toStartOfMonth(expr)}.
     *
     * @param expr the datetime expression
     * @return a toStartOfMonth expression
     */
    public static FunctionCall toStartOfMonth(Expression expr) {
        return FunctionCall.of("toStartOfMonth", expr);
    }

    /**
     * {@code toDate(expr)}.
     *
     * @param expr the expression to convert
     * @return a toDate expression
     */
    public static FunctionCall toDate(Expression expr) {
        return FunctionCall.of("toDate", expr);
    }

    /**
     * {@code toDateTime(expr)}.
     *
     * @param expr the expression to convert
     * @return a toDateTime expression
     */
    public static FunctionCall toDateTime(Expression expr) {
        return FunctionCall.of("toDateTime", expr);
    }

    /**
     * {@code toUnixTimestamp(expr)}.
     *
     * @param expr the datetime expression
     * @return a toUnixTimestamp expression
     */
    public static FunctionCall toUnixTimestamp(Expression expr) {
        return FunctionCall.of("toUnixTimestamp", expr);
    }

    /**
     * {@code now()} — current server timestamp.
     *
     * @return a now expression
     */
    public static FunctionCall now() {
        return FunctionCall.of("now");
    }

    /**
     * {@code today()} — current server date.
     *
     * @return a today expression
     */
    public static FunctionCall today() {
        return FunctionCall.of("today");
    }

    /**
     * {@code dateDiff(unit, startExpr, endExpr)}.
     *
     * @param unit      the time unit (e.g. {@code "day"}, {@code "hour"})
     * @param startExpr the start expression
     * @param endExpr   the end expression
     * @return a dateDiff expression
     */
    public static FunctionCall dateDiff(String unit, Expression startExpr, Expression endExpr) {
        return FunctionCall.of("dateDiff",
                io.blinkhouse.core.query.ast.RawFragment.of("'" + unit + "'"), startExpr, endExpr);
    }

    // ── String ─────────────────────────────────────────────────────────────────

    /**
     * {@code lower(expr)}.
     *
     * @param expr the string expression
     * @return a lower expression
     */
    public static FunctionCall lower(Expression expr) {
        return FunctionCall.of("lower", expr);
    }

    /**
     * {@code upper(expr)}.
     *
     * @param expr the string expression
     * @return an upper expression
     */
    public static FunctionCall upper(Expression expr) {
        return FunctionCall.of("upper", expr);
    }

    /**
     * {@code length(expr)}.
     *
     * @param expr the string or array expression
     * @return a length expression
     */
    public static FunctionCall length(Expression expr) {
        return FunctionCall.of("length", expr);
    }

    /**
     * {@code substring(str, offset, length)}.
     *
     * @param str    the string expression
     * @param offset the start offset (1-based)
     * @param len    the number of characters
     * @return a substring expression
     */
    public static FunctionCall substring(Expression str, Expression offset, Expression len) {
        return FunctionCall.of("substring", str, offset, len);
    }

    /**
     * {@code concat(expr1, expr2, …)}.
     *
     * @param exprs the string expressions to concatenate
     * @return a concat expression
     */
    public static FunctionCall concat(Expression... exprs) {
        return FunctionCall.of("concat", java.util.List.of(exprs));
    }

    /**
     * {@code trimBoth(expr)}.
     *
     * @param expr the string expression
     * @return a trimBoth expression
     */
    public static FunctionCall trimBoth(Expression expr) {
        return FunctionCall.of("trimBoth", expr);
    }

    /**
     * {@code splitByChar(sep, expr)}.
     *
     * @param sep  the separator character expression
     * @param expr the string to split
     * @return a splitByChar expression
     */
    public static FunctionCall splitByChar(Expression sep, Expression expr) {
        return FunctionCall.of("splitByChar", sep, expr);
    }

    // ── Array ──────────────────────────────────────────────────────────────────

    /**
     * {@code arrayJoin(expr)}.
     *
     * @param expr the array expression to expand
     * @return an arrayJoin expression
     */
    public static FunctionCall arrayJoin(Expression expr) {
        return FunctionCall.of("arrayJoin", expr);
    }

    /**
     * {@code has(array, element)}.
     *
     * @param array   the array expression
     * @param element the element to search for
     * @return a has expression
     */
    public static FunctionCall has(Expression array, Expression element) {
        return FunctionCall.of("has", array, element);
    }

    /**
     * {@code arrayLength(expr)}.
     *
     * @param expr the array expression
     * @return an arrayLength expression
     */
    public static FunctionCall arrayLength(Expression expr) {
        return FunctionCall.of("arrayLength", expr);
    }

    /**
     * {@code arrayDistinct(expr)}.
     *
     * @param expr the array expression
     * @return an arrayDistinct expression
     */
    public static FunctionCall arrayDistinct(Expression expr) {
        return FunctionCall.of("arrayDistinct", expr);
    }

    /**
     * {@code arrayElement(arr, index)}.
     *
     * @param arr   the array expression
     * @param index the 1-based index expression
     * @return an arrayElement expression
     */
    public static FunctionCall arrayElement(Expression arr, Expression index) {
        return FunctionCall.of("arrayElement", arr, index);
    }

    // ── Math ───────────────────────────────────────────────────────────────────

    /**
     * {@code abs(expr)}.
     *
     * @param expr the expression
     * @return an abs expression
     */
    public static FunctionCall abs(Expression expr) {
        return FunctionCall.of("abs", expr);
    }

    /**
     * {@code round(expr)}.
     *
     * @param expr the expression to round
     * @return a round expression
     */
    public static FunctionCall round(Expression expr) {
        return FunctionCall.of("round", expr);
    }

    /**
     * {@code round(expr, scale)}.
     *
     * @param expr  the expression to round
     * @param scale the decimal places expression
     * @return a round expression
     */
    public static FunctionCall round(Expression expr, Expression scale) {
        return FunctionCall.of("round", expr, scale);
    }

    /**
     * {@code floor(expr)}.
     *
     * @param expr the expression
     * @return a floor expression
     */
    public static FunctionCall floor(Expression expr) {
        return FunctionCall.of("floor", expr);
    }

    /**
     * {@code ceil(expr)}.
     *
     * @param expr the expression
     * @return a ceil expression
     */
    public static FunctionCall ceil(Expression expr) {
        return FunctionCall.of("ceil", expr);
    }

    /**
     * {@code log(expr)}.
     *
     * @param expr the expression
     * @return a log expression
     */
    public static FunctionCall log(Expression expr) {
        return FunctionCall.of("log", expr);
    }

    /**
     * {@code sqrt(expr)}.
     *
     * @param expr the expression
     * @return a sqrt expression
     */
    public static FunctionCall sqrt(Expression expr) {
        return FunctionCall.of("sqrt", expr);
    }

    /**
     * {@code intDiv(a, b)} — integer division.
     *
     * @param a the dividend
     * @param b the divisor
     * @return an intDiv expression
     */
    public static FunctionCall intDiv(Expression a, Expression b) {
        return FunctionCall.of("intDiv", a, b);
    }

    /**
     * {@code modulo(a, b)} — remainder.
     *
     * @param a the dividend
     * @param b the divisor
     * @return a modulo expression
     */
    public static FunctionCall modulo(Expression a, Expression b) {
        return FunctionCall.of("modulo", a, b);
    }

    // ── Type conversion ────────────────────────────────────────────────────────

    /**
     * {@code toString(expr)}.
     *
     * @param expr the expression to convert
     * @return a toString expression
     */
    public static FunctionCall toStringFn(Expression expr) {
        return FunctionCall.of("toString", expr);
    }

    /**
     * {@code toUInt64(expr)}.
     *
     * @param expr the expression to convert
     * @return a toUInt64 expression
     */
    public static FunctionCall toUInt64(Expression expr) {
        return FunctionCall.of("toUInt64", expr);
    }

    /**
     * {@code toInt64(expr)}.
     *
     * @param expr the expression to convert
     * @return a toInt64 expression
     */
    public static FunctionCall toInt64(Expression expr) {
        return FunctionCall.of("toInt64", expr);
    }

    /**
     * {@code toFloat64(expr)}.
     *
     * @param expr the expression to convert
     * @return a toFloat64 expression
     */
    public static FunctionCall toFloat64(Expression expr) {
        return FunctionCall.of("toFloat64", expr);
    }

    /**
     * {@code toDecimal128(expr, scale)}.
     *
     * @param expr  the expression to convert
     * @param scale the scale expression
     * @return a toDecimal128 expression
     */
    public static FunctionCall toDecimal128(Expression expr, Expression scale) {
        return FunctionCall.of("toDecimal128", expr, scale);
    }

    // ── Conditional ────────────────────────────────────────────────────────────

    /**
     * {@code if(cond, then, else)}.
     *
     * @param condition the condition expression
     * @param thenExpr  the value if true
     * @param elseExpr  the value if false
     * @return an if expression
     */
    public static FunctionCall ifFn(Expression condition, Expression thenExpr, Expression elseExpr) {
        return FunctionCall.of("if", condition, thenExpr, elseExpr);
    }

    /**
     * {@code coalesce(expr1, expr2, …)}.
     *
     * @param exprs the expressions to coalesce
     * @return a coalesce expression
     */
    public static FunctionCall coalesce(Expression... exprs) {
        return FunctionCall.of("coalesce", java.util.List.of(exprs));
    }

    /**
     * {@code isNull(expr)}.
     *
     * @param expr the expression to test
     * @return an isNull expression
     */
    public static FunctionCall isNullFn(Expression expr) {
        return FunctionCall.of("isNull", expr);
    }

    /**
     * {@code isNotNull(expr)}.
     *
     * @param expr the expression to test
     * @return an isNotNull expression
     */
    public static FunctionCall isNotNullFn(Expression expr) {
        return FunctionCall.of("isNotNull", expr);
    }

    // ── Networking / UUID ──────────────────────────────────────────────────────

    /**
     * {@code generateUUIDv4()}.
     *
     * @return a generateUUIDv4 expression
     */
    public static FunctionCall generateUUIDv4() {
        return FunctionCall.of("generateUUIDv4");
    }

    /**
     * {@code IPv4StringToNum(expr)}.
     *
     * @param expr the IPv4 string expression
     * @return an IPv4StringToNum expression
     */
    public static FunctionCall iPv4StringToNum(Expression expr) {
        return FunctionCall.of("IPv4StringToNum", expr);
    }

    /**
     * {@code IPv6StringToNum(expr)}.
     *
     * @param expr the IPv6 string expression
     * @return an IPv6StringToNum expression
     */
    public static FunctionCall iPv6StringToNum(Expression expr) {
        return FunctionCall.of("IPv6StringToNum", expr);
    }

    // ── AggregateFunction -Merge combinators ───────────────────────────────────

    /**
     * {@code uniqMerge(state)} — finalises a partial {@code uniq} aggregate state stored in
     * an {@code AggregateFunction(uniq, T)} column.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return a uniqMerge expression
     */
    public static FunctionCall uniqMerge(Expression stateExpr) {
        return FunctionCall.of("uniqMerge", stateExpr);
    }

    /**
     * {@code sumMerge(state)} — finalises a partial {@code sum} aggregate state.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return a sumMerge expression
     */
    public static FunctionCall sumMerge(Expression stateExpr) {
        return FunctionCall.of("sumMerge", stateExpr);
    }

    /**
     * {@code avgMerge(state)} — finalises a partial {@code avg} aggregate state.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return an avgMerge expression
     */
    public static FunctionCall avgMerge(Expression stateExpr) {
        return FunctionCall.of("avgMerge", stateExpr);
    }

    /**
     * {@code minMerge(state)} — finalises a partial {@code min} aggregate state.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return a minMerge expression
     */
    public static FunctionCall minMerge(Expression stateExpr) {
        return FunctionCall.of("minMerge", stateExpr);
    }

    /**
     * {@code maxMerge(state)} — finalises a partial {@code max} aggregate state.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return a maxMerge expression
     */
    public static FunctionCall maxMerge(Expression stateExpr) {
        return FunctionCall.of("maxMerge", stateExpr);
    }

    /**
     * {@code countMerge(state)} — finalises a partial {@code count} aggregate state.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return a countMerge expression
     */
    public static FunctionCall countMerge(Expression stateExpr) {
        return FunctionCall.of("countMerge", stateExpr);
    }

    /**
     * {@code quantileMerge(level)(state)} — finalises a partial {@code quantile} state.
     *
     * @param level     the quantile level (0..1)
     * @param stateExpr expression referencing the aggregate-state column
     * @return a quantileMerge expression
     */
    public static FunctionCall quantileMerge(double level, Expression stateExpr) {
        return FunctionCall.of("quantileMerge(" + level + ")", stateExpr);
    }

    /**
     * {@code groupArrayMerge(state)} — finalises a partial {@code groupArray} state.
     *
     * @param stateExpr expression referencing the aggregate-state column
     * @return a groupArrayMerge expression
     */
    public static FunctionCall groupArrayMerge(Expression stateExpr) {
        return FunctionCall.of("groupArrayMerge", stateExpr);
    }

    // ── Geo functions ──────────────────────────────────────────────────────────

    /**
     * {@code pointInPolygon(point, polygon)} — returns 1 if the point is inside the polygon.
     *
     * @param point   the Point expression
     * @param polygon the Polygon expression
     * @return a pointInPolygon expression
     */
    public static FunctionCall pointInPolygon(Expression point, Expression polygon) {
        return FunctionCall.of("pointInPolygon", point, polygon);
    }

    /**
     * {@code geoDistance(lon1, lat1, lon2, lat2)} — great-circle distance in metres.
     *
     * @param lon1 longitude of the first point
     * @param lat1 latitude of the first point
     * @param lon2 longitude of the second point
     * @param lat2 latitude of the second point
     * @return a geoDistance expression
     */
    public static FunctionCall geoDistance(Expression lon1, Expression lat1,
                                           Expression lon2, Expression lat2) {
        return FunctionCall.of("geoDistance", lon1, lat1, lon2, lat2);
    }

    /**
     * {@code greatCircleDistance(lon1, lat1, lon2, lat2)} — alias for {@link #geoDistance}.
     *
     * @param lon1 longitude of the first point
     * @param lat1 latitude of the first point
     * @param lon2 longitude of the second point
     * @param lat2 latitude of the second point
     * @return a greatCircleDistance expression
     */
    public static FunctionCall greatCircleDistance(Expression lon1, Expression lat1,
                                                   Expression lon2, Expression lat2) {
        return FunctionCall.of("greatCircleDistance", lon1, lat1, lon2, lat2);
    }

    /**
     * {@code H3GetBaseCell(h3index)} — returns the base cell index for an H3 cell.
     *
     * @param h3index the H3 cell index expression
     * @return an H3GetBaseCell expression
     */
    public static FunctionCall h3GetBaseCell(Expression h3index) {
        return FunctionCall.of("H3GetBaseCell", h3index);
    }

    /**
     * {@code geoToH3(lon, lat, resolution)} — converts a geo coordinate to an H3 cell index.
     *
     * @param lon        longitude expression
     * @param lat        latitude expression
     * @param resolution H3 resolution (0–15) expression
     * @return a geoToH3 expression
     */
    public static FunctionCall geoToH3(Expression lon, Expression lat, Expression resolution) {
        return FunctionCall.of("geoToH3", lon, lat, resolution);
    }

    /**
     * {@code dictGet(dictName, attr, key)} — looks up an attribute in a ClickHouse dictionary.
     *
     * <p>The dictionary name must be a compile-time constant passed as a
     * {@link io.blinkhouse.core.query.ast.RawFragment}; the key is a single expression
     * (for FLAT/HASHED layouts).
     *
     * @param dictName expression for the dictionary name (use {@code RawFragment.of("'my_dict'")})
     * @param attrName expression for the attribute name (use {@code RawFragment.of("'col'")})
     * @param key      the key expression
     * @return a dictGet expression
     */
    public static FunctionCall dictGet(Expression dictName, Expression attrName, Expression key) {
        return FunctionCall.of("dictGet", dictName, attrName, key);
    }

    /**
     * {@code dictGetOrDefault(dictName, attr, key, defaultValue)} — dictionary lookup with a fallback.
     *
     * @param dictName     expression for the dictionary name
     * @param attrName     expression for the attribute name
     * @param key          the key expression
     * @param defaultValue the value to return if the key is not found
     * @return a dictGetOrDefault expression
     */
    public static FunctionCall dictGetOrDefault(Expression dictName, Expression attrName,
                                                Expression key, Expression defaultValue) {
        return FunctionCall.of("dictGetOrDefault", dictName, attrName, key, defaultValue);
    }
}
