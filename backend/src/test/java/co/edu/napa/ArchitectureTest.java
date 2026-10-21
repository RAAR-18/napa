package co.edu.napa;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private final JavaClasses classes =
            new ClassFileImporter().importPackages("co.edu.napa");

    @Test
    void domainMustNotDependOnFrameworkOrPersistenceTypes() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("co.edu.napa..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..");

        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    void domainMustNotDependOnApplicationOrInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("co.edu.napa..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "co.edu.napa..application..",
                        "co.edu.napa..infrastructure..");

        rule.allowEmptyShould(true).check(classes);
    }
}
