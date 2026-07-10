/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    fun shouldReplaceInterfaceMapper() {
        val makeAvailableUserDto: SourceSpecs =
            java(readResource("fixtures/shouldReplaceInterfaceMapper/context/UserDto.java")) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/UserDto.java")
            }

        val makeAvailableUserEntity: SourceSpecs =
            java(readResource("fixtures/shouldReplaceInterfaceMapper/context/UserEntity.java")) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/UserEntity.java")
            }

        val makeAvailableGeneratedClass =
            java(
                readResource("fixtures/shouldReplaceInterfaceMapper/context/UserMapperImpl.java"),
                null as String?,
            ) { spec ->
                spec.path(
                    "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/UserMapperImpl.java",
                )
            }

        rewriteRun(
            makeAvailableUserDto,
            makeAvailableUserEntity,
            makeAvailableGeneratedClass,
            java(
                readResource("fixtures/shouldReplaceInterfaceMapper/before/UserService.java"),
                readResource("fixtures/shouldReplaceInterfaceMapper/after/UserService.java"),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/UserService.java")
            },
            java(
                readResource("fixtures/shouldReplaceInterfaceMapper/before/UserMapper.java"),
                readResource("fixtures/shouldReplaceInterfaceMapper/after/UserMapper.java"),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/UserMapper.java")
            },
        )
    }

    @Test
    fun shouldReplaceMappersGetMapper() {
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
    fun shouldReplaceMappersGetMapperInAnyFile() {
        val makeAvailableCustomerDto: SourceSpecs =
            java(readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerDto.java")) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
            }

        val makeAvailableCustomerEntity: SourceSpecs =
            java(readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerEntity.java")) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
            }

        val makeAvailableGeneratedClass =
            java(
                readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/context/CustomerMapperImpl.java"),
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
                readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/before/CustomerMapper.java"),
                readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/after/CustomerMapper.java"),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
            },
            java(
                readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/before/CustomerService.java"),
                readResource("fixtures/shouldReplaceMappersGetMapperInAnyFile/after/CustomerService.java"),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerService.java")
            },
        )
    }

    @Test
    fun shouldReplaceMappersGetMapperInGeneratedField() {
        val makeAvailableCustomerDto: SourceSpecs =
            java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerDto.java",
                ),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
            }

        val makeAvailableCustomerEntity: SourceSpecs =
            java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerEntity.java",
                ),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
            }

        val makeAvailableAddressMapper =
            java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapper.java",
                ),
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/AddressMapper.java",
                ),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/AddressMapper.java")
            }

        val makeAvailableAddressMapperImpl =
            java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapperImpl.java",
                ),
                null as String?,
            ) { spec ->
                spec.path(
                    "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/AddressMapperImpl.java",
                )
            }

        val makeAvailableGeneratedClass =
            java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerMapperImpl.java",
                ),
                null as String?,
            ) { spec ->
                spec.path(
                    "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java",
                )
            }

        rewriteRun(
            makeAvailableCustomerDto,
            makeAvailableCustomerEntity,
            makeAvailableAddressMapper,
            makeAvailableAddressMapperImpl,
            makeAvailableGeneratedClass,
            java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/before/CustomerMapper.java",
                ),
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/CustomerMapper.java",
                ),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
            },
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
            assert()
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
            assert()
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
            assert()
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
            assert()
        }

    companion object {
        private fun readResource(resource: String): String =
            PurgeMapstructTest::class.java.classLoader.getResourceAsStream(resource)!!.use { stream ->
                String(stream.readAllBytes(), StandardCharsets.UTF_8)
            }
    }

    operator fun invoke(action: Context.() -> Unit) {
        action(Context())
    }

    inner class Context {
        private val specs = mutableListOf<SourceSpecs>()

        fun include(file: String) {
            specs.add(
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
            specs.add(
                java(
                    readResource(from),
                    readResource(to),
                ) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/${from.split("/").last()}")
                },
            )
        }

        fun delete(file: String) {
            specs.add(
                java(
                    readResource(file),
                    null,
                ) { spec ->
                    spec.path("src/main/java/com/santunioni/fixtures/${file.split("/").last()}")
                },
            )
        }

        fun assert() {
            rewriteRun(*specs.toTypedArray())
        }
    }
}
