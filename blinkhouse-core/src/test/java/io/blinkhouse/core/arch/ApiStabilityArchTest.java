package io.blinkhouse.core.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.blinkhouse.core.annotation.Internal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit rules enforcing the BlinkHouse API-stability contract.
 *
 * <p>Rule: types in {@code *.internal.*} packages must not be accessed from
 * classes outside the {@code io.blinkhouse} package tree, and types annotated
 * with {@link Internal} must not be public API surface.
 */
class ApiStabilityArchTest {

    private static JavaClasses blinkHouseClasses;

    @BeforeAll
    static void importClasses() {
        blinkHouseClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.blinkhouse");
    }

    @Test
    void internalPackagesShouldNotBeAccessedFromOutsideBlinkHouse() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .that().resideOutsideOfPackage("io.blinkhouse..")
            .should().accessClassesThat()
            .resideInAPackage("io.blinkhouse..internal..")
            .allowEmptyShould(true);
        rule.check(blinkHouseClasses);
    }

    @Test
    void internalAnnotatedTypesShouldNotBeInPublicApi() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .that().areAnnotatedWith(Internal.class)
            .and().arePublic()
            .should().resideInAPackage("io.blinkhouse.core.annotation")
            .allowEmptyShould(true);
        rule.check(blinkHouseClasses);
    }

    @Test
    void coreModuleShouldNotDependOnSpring() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .that().resideInAPackage("io.blinkhouse.core..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..");
        rule.check(blinkHouseClasses);
    }

    @Test
    void templateShouldNotAccessRepositoryLayer() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .that().resideInAPackage("io.blinkhouse.core.template..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.blinkhouse.spring.repository..");
        rule.check(blinkHouseClasses);
    }
}
