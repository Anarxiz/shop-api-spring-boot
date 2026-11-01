package com.deliver.shop;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/** Тесты архитектуры: границы слоёв проверяются при каждой сборке. */
@AnalyzeClasses(packages = "com.deliver.shop", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule layerDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("com.deliver.shop.domain..")
            .layer("Application").definedBy("com.deliver.shop.application..")
            .layer("Infrastructure").definedBy("com.deliver.shop.infrastructure..")
            .layer("Api").definedBy("com.deliver.shop.api..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Api");

    @ArchTest
    static final ArchRule domainDoesNotUseSpring = noClasses()
            .that().resideInAPackage("com.deliver.shop.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositoriesDirectly = noClasses()
            .that().resideInAPackage("com.deliver.shop.api.controllers..")
            .should().dependOnClassesThat().resideInAPackage("com.deliver.shop.application.interfaces.repository..");

    @ArchTest
    static final ArchRule servicesLiveInServicesPackage = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("com.deliver.shop.application.services..");

    @ArchTest
    static final ArchRule controllersLiveInControllersPackage = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("com.deliver.shop.api.controllers..")
            .andShould().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith(Autowired.class);
}
