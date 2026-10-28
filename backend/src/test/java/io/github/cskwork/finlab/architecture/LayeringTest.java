package io.github.cskwork.finlab.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

class LayeringTest {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.cskwork.finlab");

    @Test
    void domainDoesNotDependOnOuterFeatureLayers() {
        noClasses().that().resideInAPackage("io.github.cskwork.finlab..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("io.github.cskwork.finlab..web..",
                        "io.github.cskwork.finlab..application..", "io.github.cskwork.finlab..persistence..")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotDependOnWeb() {
        noClasses().that().resideInAPackage("io.github.cskwork.finlab..application..")
                .should().dependOnClassesThat().resideInAPackage("io.github.cskwork.finlab..web..")
                .check(CLASSES);
    }

    @Test
    void controllersDoNotBypassApplicationServices() {
        noClasses().that().resideInAPackage("io.github.cskwork.finlab..web..")
                .should().dependOnClassesThat().resideInAPackage("io.github.cskwork.finlab..persistence..")
                .check(CLASSES);
    }

    @Test
    void persistenceDoesNotDependOnApplicationOrWeb() {
        noClasses().that().resideInAPackage("io.github.cskwork.finlab..persistence..")
                .should().dependOnClassesThat().resideInAnyPackage("io.github.cskwork.finlab..application..",
                        "io.github.cskwork.finlab..web..")
                .check(CLASSES);
    }

    @Test
    void mutableAggregatesUseOptimisticVersioning() {
        var aggregates = CLASSES.stream().filter(type -> type.isAnnotatedWith(Entity.class))
                .filter(type -> type.getSimpleName().equals("Portfolio"))
                .toList();
        assertThat(aggregates).extracting(type -> type.getSimpleName()).containsExactly("Portfolio");
        aggregates.forEach(type -> assertThat(type.getAllFields().stream().anyMatch(field -> field.isAnnotatedWith(Version.class)))
                        .as(type.getName() + " has @Version").isTrue());
    }

    @Test
    void featureServicesUseOtherFeaturesThroughApplicationInterfaces() {
        var features = Set.of("auth", "portfolio", "transaction", "holding", "goal", "plan", "metrics", "marketdata");
        for (String feature : features) {
            for (String other : features) {
                if (!feature.equals(other)) {
                    noClasses().that().resideInAPackage("io.github.cskwork.finlab." + feature + ".application..")
                            .should().dependOnClassesThat().resideInAPackage("io.github.cskwork.finlab." + other + ".persistence..")
                            .allowEmptyShould(true).check(CLASSES);
                }
            }
        }
    }

    @Test
    void featurePackagesHaveNoDependencyCycles() {
        slices().matching("io.github.cskwork.finlab.(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    void commonDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage("io.github.cskwork.finlab.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.cskwork.finlab.auth..", "io.github.cskwork.finlab.portfolio..",
                        "io.github.cskwork.finlab.transaction..", "io.github.cskwork.finlab.holding..",
                        "io.github.cskwork.finlab.goal..", "io.github.cskwork.finlab.plan..",
                        "io.github.cskwork.finlab.metrics..", "io.github.cskwork.finlab.marketdata..")
                .check(CLASSES);
    }
}
