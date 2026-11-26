package com.example.helloworld

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test

/**
 * Layer dependency rules from ADR 0003:
 *
 *     api ──▶ application ──▶ persistence
 *     job ──▶ application ──▶ provider (interfaces)
 *
 * `config` may be used by any layer. The root package (the Spring Boot application class) is not a layer.
 */
class ArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.example.helloworld")

    @Test
    fun `layers only depend downwards`() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("api").definedBy("com.example.helloworld.api..")
            .layer("job").definedBy("com.example.helloworld.job..")
            .layer("application").definedBy("com.example.helloworld.application..")
            .layer("persistence").definedBy("com.example.helloworld.persistence..")
            // Empty until the provider ticket.
            .optionalLayer("provider").definedBy("com.example.helloworld.provider..")
            .layer("config").definedBy("com.example.helloworld.config..")
            .whereLayer("api").mayNotBeAccessedByAnyLayer()
            .whereLayer("job").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("api", "job")
            .whereLayer("persistence").mayOnlyBeAccessedByLayers("application")
            .whereLayer("provider").mayOnlyBeAccessedByLayers("application")
            .check(classes)
    }
}
