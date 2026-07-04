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
import org.openrewrite.java.Assertions
import org.openrewrite.java.JavaParser
import org.openrewrite.java.format.AutoFormat
import org.openrewrite.java.tree.J
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpec
import org.openrewrite.test.SourceSpecs
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Consumer

internal class PurgeMapstructTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec
            .recipes(PurgeMapstruct(), AutoFormat("io.github.santunioni.styles.AutoFormatRecipeOutputForTest"))
            .parser(JavaParser.fromJavaVersion().classpath("mapstruct", "lombok", "junit-jupiter-api"))
    }

    @DocumentExample
    @Test
    @Throws(IOException::class)
    fun shouldReplaceInterfaceMapper() {
        val makeAvailableUserDto: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceInterfaceMapper/context/UserDto.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/UserDto.java") },
            )

        val makeAvailableUserEntity: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceInterfaceMapper/context/UserEntity.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/UserEntity.java") },
            )

        val makeAvailableGeneratedClass =
            Assertions.java(
                readResource("fixtures/shouldReplaceInterfaceMapper/context/UserMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/UserMapperImpl.java",
                    )
                },
            )

        rewriteRun(
            makeAvailableUserDto,
            makeAvailableUserEntity,
            makeAvailableGeneratedClass,
            Assertions.java(
                readResource("fixtures/shouldReplaceInterfaceMapper/before/UserService.java"),
                readResource("fixtures/shouldReplaceInterfaceMapper/after/UserService.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/UserService.java")
                },
            ),
            Assertions.java(
                readResource("fixtures/shouldReplaceInterfaceMapper/before/UserMapper.java"),
                readResource("fixtures/shouldReplaceInterfaceMapper/after/UserMapper.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/UserMapper.java") },
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun shouldReplaceMappersGetMapper() {
        val makeAvailableCustomerDto: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerDto.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/CustomerDto.java") },
            )

        val makeAvailableCustomerEntity: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerEntity.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
                },
            )

        val makeAvailableGeneratedClass =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java",
                    )
                },
            )

        rewriteRun(
            makeAvailableCustomerDto,
            makeAvailableCustomerEntity,
            makeAvailableGeneratedClass,
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapper/before/CustomerMapper.java"),
                readResource("fixtures/shouldReplaceMappersGetMapper/after/CustomerMapper.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
                },
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun shouldReplaceMappersGetMapperInGeneratedField() {
        val makeAvailableCustomerDto: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerDto.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/CustomerDto.java") },
            )

        val makeAvailableCustomerEntity: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerEntity.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
                },
            )

        val makeAvailableAddressMapper =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapper.java"),
                readResource("fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/AddressMapper.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/AddressMapper.java")
                },
            )

        val makeAvailableAddressMapperImpl =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/AddressMapperImpl.java",
                    )
                },
            )

        val makeAvailableGeneratedClass =
            Assertions.java(
                readResource("fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java",
                    )
                },
            )

        rewriteRun(
            makeAvailableCustomerDto,
            makeAvailableCustomerEntity,
            makeAvailableAddressMapper,
            makeAvailableAddressMapperImpl,
            makeAvailableGeneratedClass,
            Assertions.java(
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/before/CustomerMapper.java",
                ),
                readResource(
                    "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/CustomerMapper.java",
                ),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
                },
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun shouldRemoveAfterMappingDecorators() {
        val makeAvailableCustomerDto: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerDto.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/CustomerDto.java") },
            )

        val makeAvailableCustomerEntity: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerEntity.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
                },
            )

        val makeAvailableGeneratedClass =
            Assertions.java(
                readResource("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java",
                    )
                },
            )

        rewriteRun(
            makeAvailableCustomerDto,
            makeAvailableCustomerEntity,
            makeAvailableGeneratedClass,
            Assertions.java(
                readResource("fixtures/shouldRemoveAfterMappingDecorators/before/CustomerMapper.java"),
                readResource("fixtures/shouldRemoveAfterMappingDecorators/after/CustomerMapper.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
                },
            ),
        )
    }

    @Test
    @Throws(IOException::class)
    fun shouldRewriteWhenOnSpyToDoReturn() {
        val makeAvailableUserDto: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserDto.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/UserDto.java") },
            )

        val makeAvailableUserEntity: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserEntity.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/UserEntity.java") },
            )

        val makeAvailableGeneratedClass =
            Assertions.java(
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/UserMapperImpl.java",
                    )
                },
            )

        rewriteRun(
            Consumer { spec: RecipeSpec? ->
                spec!!.parser(
                    JavaParser
                        .fromJavaVersion()
                        .classpath("mapstruct", "lombok", "junit-jupiter-api", "mockito-core"),
                )
            },
            makeAvailableUserDto,
            makeAvailableUserEntity,
            makeAvailableGeneratedClass,
            Assertions.java(
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/before/UserMapper.java"),
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/after/UserMapper.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/UserMapper.java") },
            ),
            Assertions.java(
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/before/UserMapperSpyTest.java"),
                readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/after/UserMapperSpyTest.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/test/java/com/santunioni/fixtures/UserMapperSpyTest.java")
                },
            ),
        )
    }

    @DocumentExample
    @Test
    @Throws(IOException::class)
    fun shouldReplaceAbstractMapper() {
        val makeAvailableCustomerDto: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceAbstractMapper/context/CustomerDto.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? -> spec!!.path("src/main/java/com/santunioni/fixtures/CustomerDto.java") },
            )

        val makeAvailableCustomerEntity: SourceSpecs =
            Assertions.java(
                readResource("fixtures/shouldReplaceAbstractMapper/context/CustomerEntity.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
                },
            )

        val makeAvailableGeneratedClass =
            Assertions.java(
                readResource("fixtures/shouldReplaceAbstractMapper/context/CustomerMapperImpl.java"),
                null as String?,
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path(
                        "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java",
                    )
                },
            )

        rewriteRun(
            makeAvailableCustomerDto,
            makeAvailableCustomerEntity,
            makeAvailableGeneratedClass,
            Assertions.java(
                readResource("fixtures/shouldReplaceAbstractMapper/before/CustomerMapper.java"),
                readResource("fixtures/shouldReplaceAbstractMapper/after/CustomerMapper.java"),
                Consumer { spec: SourceSpec<J.CompilationUnit?>? ->
                    spec!!.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
                },
            ),
        )
    }

    companion object {
        @Throws(IOException::class)
        private fun readResource(resource: String?): String {
            Objects
                .requireNonNull<InputStream?>(
                    PurgeMapstructTest::class.java.getClassLoader().getResourceAsStream(resource),
                ).use { stream ->
                    return String(stream.readAllBytes(), StandardCharsets.UTF_8)
                }
        }
    }
}
