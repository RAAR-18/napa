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
                        "com.fasterxml.jackson..",
                        // Spring Boot 4 usa Jackson 3, cuyo paquete es tools.jackson
                        "tools.jackson..");

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

    @Test
    void applicationMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("co.edu.napa..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("co.edu.napa..infrastructure..");

        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    void emprendimientosDomainMustNotDependOnOtherFeatureModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("co.edu.napa.emprendimientos.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "co.edu.napa.cuentas..",
                        "co.edu.napa.notificaciones..");

        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    void productosDomainMustNotDependOnOtherFeatureModules() {
        noClasses().that().resideInAPackage("co.edu.napa.productos.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.cuentas..", "co.edu.napa.emprendimientos..", "co.edu.napa.notificaciones..")
                .check(classes);
    }

    @Test
    void tarifasDomainMustNotDependOnOtherFeatureModules() {
        noClasses().that().resideInAPackage("co.edu.napa.tarifas.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.cuentas..", "co.edu.napa.emprendimientos..",
                        "co.edu.napa.notificaciones..", "co.edu.napa.productos..")
                .check(classes);
    }

    @Test
    void pedidosDomainMustNotDependOnOtherFeatureModules() {
        noClasses().that().resideInAPackage("co.edu.napa.pedidos.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.cuentas..", "co.edu.napa.emprendimientos..",
                        "co.edu.napa.notificaciones..", "co.edu.napa.productos..",
                        "co.edu.napa.tarifas..")
                .check(classes);
    }

    @Test
    void pagosDomainMustNotDependOnOtherFeatureModules() {
        noClasses().that().resideInAPackage("co.edu.napa.pagos.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.cuentas..", "co.edu.napa.emprendimientos..",
                        "co.edu.napa.notificaciones..", "co.edu.napa.productos..",
                        "co.edu.napa.pedidos..", "co.edu.napa.tarifas..")
                .check(classes);
    }

    @Test
    void domiciliosDomainMustNotDependOnOtherFeatureModules() {
        noClasses().that().resideInAPackage("co.edu.napa.domicilios.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.cuentas..", "co.edu.napa.emprendimientos..",
                        "co.edu.napa.notificaciones..", "co.edu.napa.productos..",
                        "co.edu.napa.pedidos..", "co.edu.napa.pagos..", "co.edu.napa.tarifas..")
                .check(classes);
    }

    @Test
    void domiciliosUsesOtherModulesOnlyThroughIncomingPorts() {
        noClasses().that().resideInAPackage("co.edu.napa.domicilios..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.pedidos.domain..", "co.edu.napa.pedidos.application.ports.out..",
                        "co.edu.napa.pedidos.application.usecase..", "co.edu.napa.pedidos.infrastructure..",
                        "co.edu.napa.pagos.domain..", "co.edu.napa.pagos.application.ports.out..",
                        "co.edu.napa.pagos.application.usecase..", "co.edu.napa.pagos.infrastructure..",
                        "co.edu.napa.emprendimientos.domain..", "co.edu.napa.emprendimientos.application.ports.out..",
                        "co.edu.napa.emprendimientos.application.usecase..",
                        "co.edu.napa.emprendimientos.infrastructure..")
                .check(classes);
    }

    @Test
    void productosApplicationUsesOnlyTheIncomingEmprendimientosPort() {
        noClasses().that().resideInAPackage("co.edu.napa.productos.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.edu.napa.emprendimientos.domain..", "co.edu.napa.emprendimientos.infrastructure..",
                        "co.edu.napa.emprendimientos.application.ports.out..", "co.edu.napa.emprendimientos.application.usecase..")
                .check(classes);
    }

    @Test
    void webControllersMustNotDependOnUseCaseImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "co.edu.napa..infrastructure.adapters.in.web..",
                        "co.edu.napa..infrastructure.web..")
                .should().dependOnClassesThat()
                .resideInAPackage("co.edu.napa..application.usecase..");

        rule.allowEmptyShould(true).check(classes);
    }

    @Test
    void incomingPortsMustNotDependOnUseCaseImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("co.edu.napa..application.ports.in..")
                .should().dependOnClassesThat()
                .resideInAPackage("co.edu.napa..application.usecase..");

        rule.allowEmptyShould(true).check(classes);
    }
}
