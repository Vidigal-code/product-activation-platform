package com.vidigal.activation.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.vidigal.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_HAS_NO_APPLICATION_OR_ADAPTER_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..request.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..request.api..", "..request.application..",
                    "..request.persistence..", "..request.integration..", "..security..")
            .because("regras de domínio devem permanecer independentes dos pontos de entrada e adaptadores");

    @ArchTest
    static final ArchRule API_DOES_NOT_ACCESS_PERSISTENCE_DIRECTLY = noClasses()
            .that().resideInAPackage("..request.api..")
            .should().dependOnClassesThat().resideInAPackage("..request.persistence..")
            .because("a API deve delegar casos de uso à camada de aplicação");

    @ArchTest
    static final ArchRule PROVIDER_INTEGRATION_DOES_NOT_DEPEND_ON_API_OR_PERSISTENCE = noClasses()
            .that().resideInAPackage("..request.integration..")
            .should().dependOnClassesThat().resideInAnyPackage("..request.api..", "..request.persistence..")
            .because("o contrato externo não deve depender de apresentação ou armazenamento interno");
}
