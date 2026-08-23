/**
 * Annotation processor — generates compile-time metamodel classes ({@code PageView_}, etc.).
 *
 * <p>This module is <strong>optional</strong>. The string-based API ({@code col("ts")})
 * works without it. Add this processor only when compile-time column safety is required
 * (ADR-05, risk R-6).
 *
 * <ul>
 *   <li>{@code MetamodelProcessor} — annotation processor: scans {@code @ChTable} classes,
 *       emits {@code Xxx_} metamodel sources</li>
 *   <li>{@code MetamodelWriter} — writes the generated source files</li>
 * </ul>
 *
 * <p>Generated output example for {@code PageView}:
 * <pre>{@code
 * public final class PageView_ {
 *     public static final TableRef TABLE = TableRef.of("analytics", "page_views");
 *     public static final Column<PageView, Integer> TENANT_ID = ...;
 *     public static final Column<PageView, Instant>  TS        = ...;
 * }
 * }</pre>
 */
package io.blinkhouse.processor;
