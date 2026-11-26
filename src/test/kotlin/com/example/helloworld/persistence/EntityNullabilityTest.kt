package com.example.helloworld.persistence

import com.example.helloworld.PostgresTestConfiguration
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.metamodel.mapping.AttributeMapping
import org.hibernate.metamodel.mapping.ModelPart
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.lang.Nullable
import java.lang.reflect.Method

/**
 * Hibernate `validate` checks that columns exist and have the right type, but not whether they
 * accept NULL. This test closes that gap (ADR 0001): an entity getter is marked `@Nullable`
 * exactly when its column is nullable in PostgreSQL, so Kotlin code sees the real nullability.
 */
@SpringBootTest(properties = ["app.retention.enabled=false"])
@Import(PostgresTestConfiguration::class)
class EntityNullabilityTest(
    @Autowired private val entityManagerFactory: EntityManagerFactory,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private data class Column(val table: String, val name: String)

    @Test
    fun `entity getters are @Nullable exactly when their column is nullable`() {
        val columnIsNullable: Map<Column, Boolean> = jdbc.query(
            "SELECT table_name, column_name, is_nullable FROM information_schema.columns WHERE table_schema = 'public'",
        ) { rs, _ -> Column(rs.getString(1), rs.getString(2)) to (rs.getString(3) == "YES") }.toMap()

        val mismatches = mutableListOf<String>()
        var checked = 0

        val metamodel = entityManagerFactory.unwrap(SessionFactoryImplementor::class.java).mappingMetamodel
        metamodel.forEachEntityDescriptor { entity ->
            val type = entity.mappedClass
            val parts = mutableListOf<Pair<String, ModelPart>>()
            parts += entity.identifierPropertyName to entity.identifierMapping
            entity.forEachAttributeMapping { attribute: AttributeMapping -> parts += attribute.attributeName to attribute }

            for ((attributeName, part) in parts) {
                val getter = findGetter(type, attributeName)
                    ?: error("${type.simpleName}.$attributeName has no getter; add one or exclude it explicitly")
                part.forEachSelectable { _, selectable ->
                    val column = Column(selectable.containingTableExpression, selectable.selectionExpression)
                    val dbNullable = columnIsNullable[column]
                        ?: error("${type.simpleName}.$attributeName maps to ${column.table}.${column.name}, which does not exist")
                    val markedNullable = getter.isAnnotationPresent(Nullable::class.java)
                    checked++
                    when {
                        getter.returnType.isPrimitive && dbNullable ->
                            mismatches += "${type.simpleName}.${getter.name}() returns a primitive, but ${column.table}.${column.name} is nullable"
                        !getter.returnType.isPrimitive && dbNullable != markedNullable ->
                            mismatches += "${type.simpleName}.${getter.name}(): column ${column.table}.${column.name} is " +
                                (if (dbNullable) "nullable, add @Nullable" else "NOT NULL, remove @Nullable")
                    }
                }
            }
        }

        assertThat(checked).`as`("columns checked").isGreaterThan(100)
        assertThat(mismatches.sorted()).`as`("getter nullability that differs from the database").isEmpty()
    }

    private fun findGetter(type: Class<*>, attributeName: String): Method? {
        val suffix = attributeName.replaceFirstChar(Char::uppercaseChar)
        return listOf("get$suffix", "is$suffix").firstNotNullOfOrNull { name ->
            runCatching { type.getMethod(name) }.getOrNull()
        }
    }
}
