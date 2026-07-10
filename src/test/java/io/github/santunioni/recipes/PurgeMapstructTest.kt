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
                    "spring-test",
                    "spring-boot-starter-test",
                ),
            )
    }

    @DocumentExample
    @Test
    fun shouldReplaceInterfaceMapper() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldReplaceInterfaceMapper/context/UserDto.java")
            makeAvailable("fixtures/shouldReplaceInterfaceMapper/context/UserEntity.java")

            // Assert
            expectDeleted("fixtures/shouldReplaceInterfaceMapper/context/UserMapperImpl.java")
            expectTransformed(
                "fixtures/shouldReplaceInterfaceMapper/before/UserMapper.java",
                "fixtures/shouldReplaceInterfaceMapper/after/UserMapper.java",
            )
            expectTransformed(
                "fixtures/shouldReplaceInterfaceMapper/before/UserService.java",
                "fixtures/shouldReplaceInterfaceMapper/after/UserService.java",
            )
        }

    @Test
    fun shouldReplaceMappersGetMapper() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldReplaceMappersGetMapper/context/CustomerDto.java")
            makeAvailable("fixtures/shouldReplaceMappersGetMapper/context/CustomerEntity.java")

            // Assert
            expectDeleted("fixtures/shouldReplaceMappersGetMapper/context/CustomerMapperImpl.java")

            expectTransformed(
                "fixtures/shouldReplaceMappersGetMapper/before/CustomerMapper.java",
                "fixtures/shouldReplaceMappersGetMapper/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldReplaceMappersGetMapperInAnyFile() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerDto.java")
            makeAvailable("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerEntity.java")

            // Assert
            expectDeleted("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerMapperImpl.java")
            expectTransformed(
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/before/CustomerMapper.java",
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/after/CustomerMapper.java",
            )
            expectTransformed(
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/before/CustomerService.java",
                "fixtures/shouldReplaceMappersGetMapperInAnyFile/after/CustomerService.java",
            )
        }

    @Test
    fun shouldReplaceMappersGetMapperInGeneratedField() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerDto.java")
            makeAvailable("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerEntity.java")

            // Assert
            expectTransformed(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapper.java",
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/AddressMapper.java",
            )
            expectDeleted("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapperImpl.java")
            expectDeleted("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerMapperImpl.java")
            expectTransformed(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/before/CustomerMapper.java",
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldRemoveAfterMappingDecorators() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerDto.java")
            makeAvailable("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerEntity.java")

            // Assert
            expectDeleted("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerMapperImpl.java")
            expectTransformed(
                "fixtures/shouldRemoveAfterMappingDecorators/before/CustomerMapper.java",
                "fixtures/shouldRemoveAfterMappingDecorators/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldRewriteWhenOnSpyToDoReturn() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserDto.java")
            makeAvailable("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserEntity.java")

            // Assert
            expectDeleted("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserMapperImpl.java")
            expectTransformed(
                "fixtures/shouldRewriteWhenOnSpyToDoReturn/before/UserMapper.java",
                "fixtures/shouldRewriteWhenOnSpyToDoReturn/after/UserMapper.java",
            )
        }

    @DocumentExample
    @Test
    fun shouldReplaceAbstractMapper() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldReplaceAbstractMapper/context/CustomerDto.java")
            makeAvailable("fixtures/shouldReplaceAbstractMapper/context/CustomerEntity.java")

            // Assert
            expectDeleted("fixtures/shouldReplaceAbstractMapper/context/CustomerMapperImpl.java")
            expectTransformed(
                "fixtures/shouldReplaceAbstractMapper/before/CustomerMapper.java",
                "fixtures/shouldReplaceAbstractMapper/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldInlineDecoratedMapper() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldInlineDecoratedMapper/context/SourceEntity.java")
            makeAvailable("fixtures/shouldInlineDecoratedMapper/context/TargetDto.java")

            // Assert
            expectDeleted("fixtures/shouldInlineDecoratedMapper/context/FooMapperImpl.java")
            expectDeleted("fixtures/shouldInlineDecoratedMapper/context/FooMapperImpl_.java")
            expectDeleted("fixtures/shouldInlineDecoratedMapper/before/FooMapperDecorator.java")
            expectTransformed(
                "fixtures/shouldInlineDecoratedMapper/before/FooMapper.java",
                "fixtures/shouldInlineDecoratedMapper/after/FooMapper.java",
            )
        }

    @Test
    fun shouldStripContextTypeAnnotation() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldStripContextTypeAnnotation/context/CustomerDto.java")
            makeAvailable("fixtures/shouldStripContextTypeAnnotation/context/CustomerEntity.java")

            // Assert
            expectDeleted("fixtures/shouldStripContextTypeAnnotation/context/CustomerMapperImpl.java")
            expectTransformed(
                "fixtures/shouldStripContextTypeAnnotation/before/CustomerMapper.java",
                "fixtures/shouldStripContextTypeAnnotation/after/CustomerMapper.java",
            )
        }

    @Test
    fun shouldRewriteImportMapperImpl() =
        simulate {
            // Arrange
            makeAvailable("fixtures/shouldRewriteImportMapperImpl/context/CustomerDto.java")
            makeAvailable("fixtures/shouldRewriteImportMapperImpl/context/CustomerEntity.java")

            // Assert
            expectDeleted("fixtures/shouldRewriteImportMapperImpl/context/CustomerMapperImpl.java")
            expectTransformed(
                "fixtures/shouldRewriteImportMapperImpl/before/CustomerMapper.java",
                "fixtures/shouldRewriteImportMapperImpl/after/CustomerMapper.java",
            )
            expectTransformed(
                "fixtures/shouldRewriteImportMapperImpl/before/CustomerServiceTest.java",
                "fixtures/shouldRewriteImportMapperImpl/after/CustomerServiceTest.java",
            )
        }
}
