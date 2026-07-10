package io.github.santunioni.recipes

import org.junit.jupiter.api.Test
import org.openrewrite.DocumentExample
import org.openrewrite.java.Assertions.java
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpecs
import java.nio.charset.StandardCharsets

internal class PurgeMapstructTest : RewriteTest {
    @DocumentExample
    @Test
    fun shouldReplaceInterfaceMapper() =
        rewrite {
            // Arrange
            include("fixtures/shouldReplaceInterfaceMapper/context/UserDto.java")
            include("fixtures/shouldReplaceInterfaceMapper/context/UserEntity.java")

            // Act - Assert
            delete("fixtures/shouldReplaceInterfaceMapper/context/UserMapperImpl.java")
            transform(
                "fixtures/shouldReplaceInterfaceMapper/before/UserMapper.java",
                "fixtures/shouldReplaceInterfaceMapper/after/UserMapper.java",
            )
            transform(
                "fixtures/shouldReplaceInterfaceMapper/before/UserService.java",
                "fixtures/shouldReplaceInterfaceMapper/after/UserService.java",
            )
        }

    @Test
    fun shouldReplaceMappersGetMapper() =
        rewrite {
            val makeAvailableCustomerDto: SourceSpecs =
                java(readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerDto.java")) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
                }

            val makeAvailableCustomerEntity: SourceSpecs =
                java(readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerEntity.java")) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
                }

            val makeAvailableGeneratedClass =
                java(
                    readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerMapperImpl.java"),
                    null as String?,
                ) { spec ->
                    spec.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java",
                    )
                }

            rewriteRun(
                makeAvailableCustomerDto,
                makeAvailableCustomerEntity,
                makeAvailableGeneratedClass,
                java(
                    readResource("fixtures/shouldReplaceMappersGetMapper/before/CustomerMapper.java"),
                    readResource("fixtures/shouldReplaceMappersGetMapper/after/CustomerMapper.java"),
                ) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
                },
            )
        }

    @Test
    fun shouldReplaceMappersGetMapperInAnyFile() =
        rewrite {
            // Arrange
            include("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerDto.java")
            include("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerEntity.java")

            // Act - Assert
            delete("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerMapperImpl.java")
            transform(
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/before/CustomerMapper.java",
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/after/CustomerMapper.java",
            )
            transform(
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/before/CustomerService.java",
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/after/CustomerService.java",
            )
        }

    @Test
    fun shouldReplaceMappersGetMapperInGeneratedField() =
        rewrite {
            // Arrange
            include("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerDto.java")
            include("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerEntity.java")

            // Act - Assert
            transform(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapper.java",
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/AddressMapper.java",
            )
            delete("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapperImpl.java")
            delete("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerMapperImpl.java")
            transform(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/before/CustomerMapper.java",
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldRemoveAfterMappingDecorators() =
        rewrite {
            // Arrange
            include("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerDto.java")
            include("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerEntity.java")

            // Act - Assert
            delete("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerMapperImpl.java")
            transform(
                "fixtures/shouldRemoveAfterMappingDecorators/before/CustomerMapper.java",
                "fixtures/shouldRemoveAfterMappingDecorators/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldRewriteWhenOnSpyToDoReturn() =
        rewrite {
            // Arrange
            include("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserDto.java")
            include("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserEntity.java")

            // Act - Assert
            delete("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserMapperImpl.java")
            transform(
                "fixtures/shouldRewriteWhenOnSpyToDoReturn/before/UserMapper.java",
                "fixtures/shouldRewriteWhenOnSpyToDoReturn/after/UserMapper.java",
            )
        }

    @DocumentExample
    @Test
    fun shouldReplaceAbstractMapper() =
        rewrite {
            // Arrange
            include("fixtures/shouldReplaceAbstractMapper/context/CustomerDto.java")
            include("fixtures/shouldReplaceAbstractMapper/context/CustomerEntity.java")

            // Act - Assert
            delete("fixtures/shouldReplaceAbstractMapper/context/CustomerMapperImpl.java")
            transform(
                "fixtures/shouldReplaceAbstractMapper/before/CustomerMapper.java",
                "fixtures/shouldReplaceAbstractMapper/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldInlineDecoratedMapper() =
        rewrite {
            // Arrange
            include("fixtures/decoratedWith/context/SourceEntity.java")
            include("fixtures/decoratedWith/context/TargetDto.java")

            // Act - Assert
            delete("fixtures/decoratedWith/context/FooMapperImpl.java")
            delete("fixtures/decoratedWith/context/FooMapperImpl_.java")
            delete("fixtures/decoratedWith/before/FooMapperDecorator.java")
            transform(
                "fixtures/decoratedWith/before/FooMapper.java",
                "fixtures/decoratedWith/after/FooMapper.java",
            )
        }

    @Test
    fun shouldStripContextTypeAnnotation() =
        rewrite {
            // Arrange
            include("fixtures/shouldStripContextTypeAnnotation/context/CustomerDto.java")
            include("fixtures/shouldStripContextTypeAnnotation/context/CustomerEntity.java")

            // Act - Assert
            delete("fixtures/shouldStripContextTypeAnnotation/context/CustomerMapperImpl.java")
            transform(
                "fixtures/shouldStripContextTypeAnnotation/before/CustomerMapper.java",
                "fixtures/shouldStripContextTypeAnnotation/after/CustomerMapper.java",
            )
        }

    companion object {
        private fun readResource(resource: String): String =
            PurgeMapstructTest::class.java.classLoader.getResourceAsStream(resource)!!.use { stream ->
                String(stream.readAllBytes(), StandardCharsets.UTF_8)
            }
    }

    fun rewrite(action: RecipeTestSpecification.() -> Unit) {
        val ctx = RecipeTestSpecification()
        action(ctx)
        rewriteRun(*ctx.sourceSpecs.toTypedArray())
    }

    override fun defaults(spec: RecipeSpec) {
        spec
            .recipes(
                PurgeMapstruct(),
            ).parser(
                JavaParser.fromJavaVersion().classpath(
                    "mapstruct",
                    "lombok",
                    "junit-jupiter-api",
                    "spring-beans",
                    "spring-context",
                ),
            )
    }

    class RecipeTestSpecification {
        val sourceSpecs = mutableListOf<SourceSpecs>()

        fun include(file: String) {
            sourceSpecs.add(
                java(
                    readResource(file),
                ) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/${file.split("/").last()}")
                },
            )
        }

        fun transform(
            from: String,
            to: String,
        ) {
            sourceSpecs.add(
                java(
                    readResource(from),
                    readResource(to),
                ) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/${from.split("/").last()}")
                },
            )
        }

        fun delete(file: String) {
            sourceSpecs.add(
                java(
                    readResource(file),
                    null,
                ) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/${file.split("/").last()}")
                },
            )
        }
    }
}
