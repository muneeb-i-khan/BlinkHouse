/**
 * Query AST, SQL renderer, parameter binding, and fluent DSL.
 *
 * <p><strong>Security invariant (NFR-6):</strong> the renderer emits {@code {name:Type}}
 * placeholders for every {@code ParameterRef}. Literal nodes may only be produced from
 * internal constants — there is no {@code LiteralFromUserInput} type. All user values
 * are bound server-side; none are interpolated into the SQL string.
 *
 * <ul>
 *   <li>{@code SelectStatement} — root AST node</li>
 *   <li>{@code Expression} / {@code Predicate} — sealed node hierarchy</li>
 *   <li>{@code SqlRenderer} — AST → parameterised SQL string</li>
 *   <li>{@code ParameterBinder} — named parameter extraction and type-mapping</li>
 *   <li>{@code BoundStatement} — rendered SQL + bound parameter map</li>
 *   <li>{@code ChQuery} — fluent DSL entry point ({@code ChQuery.from(...).select(...).where(...)})</li>
 *   <li>{@code Functions} — aggregate, date, array, and string function library</li>
 * </ul>
 */
package io.blinkhouse.core.query;
