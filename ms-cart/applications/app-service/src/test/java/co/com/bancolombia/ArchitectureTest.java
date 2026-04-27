package co.com.bancolombia;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void domainShouldNotDependOnSpring() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("co.com.bancolombia.model", "co.com.bancolombia.usecase");
        noClasses()
                .that().resideInAPackage("co.com.bancolombia.model..")
                .or().resideInAPackage("co.com.bancolombia.usecase..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .check(classes);
    }
}
