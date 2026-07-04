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

import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Test
import org.openrewrite.DocumentExample
import org.openrewrite.java.Assertions.java
import org.openrewrite.java.JavaParser
import org.openrewrite.java.format.AutoFormat
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpecs

internal class PurgeMapstructTest : RewriteTest {
  override fun defaults(spec: RecipeSpec) {
    spec
        .recipes(
            PurgeMapstruct(),
            AutoFormat("io.github.santunioni.styles.AutoFormatRecipeOutputForTest"),
        )
        .parser(JavaParser.fromJavaVersion().classpath("mapstruct", "lombok", "junit-jupiter-api"))
  }

  @DocumentExample
  @Test
  fun shouldReplaceInterfaceMapper() {
    val makeAvailableUserDto: SourceSpecs =
        java(readResource("fixtures/shouldReplaceInterfaceMapper/context/UserDto.java")) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/UserDto.java")
        }

    val makeAvailableUserEntity: SourceSpecs =
        java(readResource("fixtures/shouldReplaceInterfaceMapper/context/UserEntity.java")) { spec
          ->
          spec.path("src/main/java/com/santunioni/fixtures/UserEntity.java")
        }

    val makeAvailableGeneratedClass =
        java(
            readResource("fixtures/shouldReplaceInterfaceMapper/context/UserMapperImpl.java"),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/UserMapperImpl.java"
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
        java(readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerDto.java")) { spec
          ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
        }

    val makeAvailableCustomerEntity: SourceSpecs =
        java(readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerEntity.java")) {
            spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
        }

    val makeAvailableGeneratedClass =
        java(
            readResource("fixtures/shouldReplaceMappersGetMapper/context/CustomerMapperImpl.java"),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java"
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
  fun shouldReplaceMappersGetMapperInGeneratedField() {
    val makeAvailableCustomerDto: SourceSpecs =
        java(
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerDto.java"
            )
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
        }

    val makeAvailableCustomerEntity: SourceSpecs =
        java(
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerEntity.java"
            )
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
        }

    val makeAvailableAddressMapper =
        java(
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapper.java"
            ),
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/AddressMapper.java"
            ),
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/AddressMapper.java")
        }

    val makeAvailableAddressMapperImpl =
        java(
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/AddressMapperImpl.java"
            ),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/AddressMapperImpl.java"
          )
        }

    val makeAvailableGeneratedClass =
        java(
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/context/CustomerMapperImpl.java"
            ),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java"
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
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/before/CustomerMapper.java"
            ),
            readResource(
                "fixtures/shouldReplaceMappersGetMapperInGeneratedField/after/CustomerMapper.java"
            ),
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
        },
    )
  }

  @Test
  fun shouldRemoveAfterMappingDecorators() {
    val makeAvailableCustomerDto: SourceSpecs =
        java(
            readResource("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerDto.java")
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
        }

    val makeAvailableCustomerEntity: SourceSpecs =
        java(
            readResource("fixtures/shouldRemoveAfterMappingDecorators/context/CustomerEntity.java")
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
        }

    val makeAvailableGeneratedClass =
        java(
            readResource(
                "fixtures/shouldRemoveAfterMappingDecorators/context/CustomerMapperImpl.java"
            ),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java"
          )
        }

    rewriteRun(
        makeAvailableCustomerDto,
        makeAvailableCustomerEntity,
        makeAvailableGeneratedClass,
        java(
            readResource("fixtures/shouldRemoveAfterMappingDecorators/before/CustomerMapper.java"),
            readResource("fixtures/shouldRemoveAfterMappingDecorators/after/CustomerMapper.java"),
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
        },
    )
  }

  @Test
  fun shouldRewriteWhenOnSpyToDoReturn() {
    val makeAvailableUserDto: SourceSpecs =
        java(readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserDto.java")) { spec
          ->
          spec.path("src/main/java/com/santunioni/fixtures/UserDto.java")
        }

    val makeAvailableUserEntity: SourceSpecs =
        java(readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserEntity.java")) {
            spec ->
          spec.path("src/main/java/com/santunioni/fixtures/UserEntity.java")
        }

    val makeAvailableGeneratedClass =
        java(
            readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/context/UserMapperImpl.java"),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/UserMapperImpl.java"
          )
        }

    rewriteRun(
        { spec: RecipeSpec ->
          spec.parser(
              JavaParser.fromJavaVersion()
                  .classpath(
                      "mapstruct",
                      "lombok",
                      "junit-jupiter-api",
                      "mockito-core",
                  )
          )
        },
        makeAvailableUserDto,
        makeAvailableUserEntity,
        makeAvailableGeneratedClass,
        java(
            readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/before/UserMapper.java"),
            readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/after/UserMapper.java"),
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/UserMapper.java")
        },
        java(
            readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/before/UserMapperSpyTest.java"),
            readResource("fixtures/shouldRewriteWhenOnSpyToDoReturn/after/UserMapperSpyTest.java"),
        ) { spec ->
          spec.path("src/test/java/com/santunioni/fixtures/UserMapperSpyTest.java")
        },
    )
  }

  @DocumentExample
  @Test
  fun shouldReplaceAbstractMapper() {
    val makeAvailableCustomerDto: SourceSpecs =
        java(readResource("fixtures/shouldReplaceAbstractMapper/context/CustomerDto.java")) { spec
          ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerDto.java")
        }

    val makeAvailableCustomerEntity: SourceSpecs =
        java(readResource("fixtures/shouldReplaceAbstractMapper/context/CustomerEntity.java")) {
            spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerEntity.java")
        }

    val makeAvailableGeneratedClass =
        java(
            readResource("fixtures/shouldReplaceAbstractMapper/context/CustomerMapperImpl.java"),
            null as String?,
        ) { spec ->
          spec.path(
              "build/generated/annotationProcessor/main/java/com/santunioni/fixtures/CustomerMapperImpl.java"
          )
        }

    rewriteRun(
        makeAvailableCustomerDto,
        makeAvailableCustomerEntity,
        makeAvailableGeneratedClass,
        java(
            readResource("fixtures/shouldReplaceAbstractMapper/before/CustomerMapper.java"),
            readResource("fixtures/shouldReplaceAbstractMapper/after/CustomerMapper.java"),
        ) { spec ->
          spec.path("src/main/java/com/santunioni/fixtures/CustomerMapper.java")
        },
    )
  }

  companion object {
    private fun readResource(resource: String): String =
        PurgeMapstructTest::class.java.classLoader.getResourceAsStream(resource)!!.use { stream ->
          String(stream.readAllBytes(), StandardCharsets.UTF_8)
        }
  }
}
