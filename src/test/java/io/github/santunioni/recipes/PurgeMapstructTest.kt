package io.github.santunioni.recipes

import org.junit.jupiter.api.Test
import org.openrewrite.DocumentExample
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

internal class PurgeMapstructTest : RewriteTest {
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

    @DocumentExample
    @Test
    fun shouldReplaceInterfaceMapper() =
        this {
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
        this {
            // Arrange
            include("fixtures/shouldReplaceMappersGetMapper/context/CustomerDto.java")
            include("fixtures/shouldReplaceMappersGetMapper/context/CustomerEntity.java")

            // Act - Assert
            delete("fixtures/shouldReplaceMappersGetMapper/context/CustomerMapperImpl.java")

            transform(
                "fixtures/shouldReplaceMappersGetMapper/before/CustomerMapper.java",
                "fixtures/shouldReplaceMappersGetMapper/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldReplaceMappersGetMapperInAnyFile() =
        this {
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
        this {
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
        this {
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
        this {
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
        this {
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
        this {
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
        this {
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
}
