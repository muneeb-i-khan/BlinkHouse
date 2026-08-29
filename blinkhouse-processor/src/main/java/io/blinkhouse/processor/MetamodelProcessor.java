package io.blinkhouse.processor;

import com.google.auto.service.AutoService;
import io.blinkhouse.core.annotation.ChColumn;
import io.blinkhouse.core.annotation.ChTable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor that generates {@code Xxx_} metamodel classes for entities
 * annotated with {@link ChTable}.
 *
 * <p>For each annotated entity class, a sibling class named {@code ClassName_} is
 * generated in the same package. It exposes one
 * {@code public static final Column<Entity, FieldType>} field per mapped column.
 *
 * <p>The generated class is OPTIONAL — the string-based {@code col("name")} API
 * works identically without this processor (ADR-05).
 *
 * <p>Auto-registered via {@link AutoService} for zero-config discovery.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.blinkhouse.core.annotation.ChTable")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class MetamodelProcessor extends AbstractProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ChTable.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            try {
                generateMetamodelClass(typeElement);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                        javax.tools.Diagnostic.Kind.ERROR,
                        "Failed to generate metamodel for " + typeElement.getQualifiedName() + ": " + e.getMessage(),
                        typeElement);
            }
        }
        return true;
    }

    private void generateMetamodelClass(TypeElement entity) throws IOException {
        String qualifiedName = entity.getQualifiedName().toString();
        String simpleName = entity.getSimpleName().toString();
        String packageName = qualifiedName.contains(".")
                ? qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
                : "";
        String metamodelName = simpleName + "_";
        String fullMetamodelName = packageName.isEmpty() ? metamodelName : packageName + "." + metamodelName;

        List<ColumnField> columns = collectColumns(entity);

        JavaFileObject file = processingEnv.getFiler().createSourceFile(fullMetamodelName, entity);
        try (PrintWriter writer = new PrintWriter(file.openWriter())) {
            if (!packageName.isEmpty()) {
                writer.println("package " + packageName + ";");
                writer.println();
            }
            writer.println("import io.blinkhouse.core.query.metamodel.Column;");
            writer.println();
            writer.println("/**");
            writer.println(" * Generated metamodel for {@link " + simpleName + "}.");
            writer.println(" * Do not edit — regenerated on each build.");
            writer.println(" */");
            writer.println("@javax.annotation.processing.Generated(\"io.blinkhouse.processor.MetamodelProcessor\")");
            writer.println("public final class " + metamodelName + " {");
            writer.println();
            writer.println("    private " + metamodelName + "() {}");
            writer.println();
            for (ColumnField col : columns) {
                writer.println("    /** Column {@code " + col.columnName + "} of type {@code " + col.javaTypeName + "}. */");
                writer.println("    public static final Column<" + simpleName + ", " + col.javaTypeName + "> "
                        + col.fieldName + " =");
                writer.println("            new Column<>(\"" + col.columnName + "\", " + col.javaTypeName + ".class);");
                writer.println();
            }
            writer.println("}");
        }
    }

    private List<ColumnField> collectColumns(TypeElement entity) {
        List<ColumnField> result = new ArrayList<>();
        for (Element enclosed : entity.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement field = (VariableElement) enclosed;
            String columnName = resolveColumnName(field);
            String fieldName = field.getSimpleName().toString();
            String javaTypeName = boxedTypeName(field.asType());
            result.add(new ColumnField(fieldName, columnName, javaTypeName));
        }
        return result;
    }

    private String resolveColumnName(VariableElement field) {
        ChColumn annotation = field.getAnnotation(ChColumn.class);
        if (annotation != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }
        return field.getSimpleName().toString();
    }

    /** Converts a TypeMirror to a boxed simple class name for use in generated code. */
    private String boxedTypeName(TypeMirror type) {
        switch (type.getKind()) {
            case LONG: return "Long";
            case INT: return "Integer";
            case SHORT: return "Short";
            case BYTE: return "Byte";
            case DOUBLE: return "Double";
            case FLOAT: return "Float";
            case BOOLEAN: return "Boolean";
            case CHAR: return "Character";
            default:
                String raw = type.toString();
                return raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
        }
    }

    private static final class ColumnField {
        final String fieldName;
        final String columnName;
        final String javaTypeName;

        ColumnField(String fieldName, String columnName, String javaTypeName) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.javaTypeName = javaTypeName;
        }
    }
}
