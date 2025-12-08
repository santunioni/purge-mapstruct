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
package com.santunioni.recipes;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.format.AutoFormat;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.openrewrite.java.Assertions.java;

class RemoveMapstructTest implements RewriteTest {
    private static @NonNull String readResource(String resource) throws IOException {
        try (InputStream stream = Objects.requireNonNull(
          RemoveMapstructTest.class.getClassLoader()
            .getResourceAsStream(resource))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new RemoveMapstruct(), new AutoFormat("com.santunioni.styles.AutoFormatRecipeOutputForTest", false))
          .parser(JavaParser.fromJavaVersion()
            .classpath("mapstruct"));
    }

    @DocumentExample
    @Test
    void replaceSimpleDtoMapper() throws IOException {
        SourceSpecs makeAvailableSimpleDtoIn = java(
          readResource("fixtures/replaceSimpleDtoMapper/context/SimpleDtoIn.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoIn.java")
        );

        SourceSpecs makeAvailableSimpleDtoOut = java(
          readResource("fixtures/replaceSimpleDtoMapper/context/SimpleDtoOut.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoOut.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readResource("fixtures/replaceSimpleDtoMapper/context/SimpleDtoMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapperImpl.java")
        );

        rewriteRun(
          makeAvailableSimpleDtoIn,
          makeAvailableSimpleDtoOut,
          makeAvailableGeneratedClass,
          java(
            readResource("fixtures/replaceSimpleDtoMapper/before/SimpleDtoMapper.java"),
            readResource("fixtures/replaceSimpleDtoMapper/after/SimpleDtoMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapper.java")
          )
        );
    }

    @DocumentExample
    @Test
    void replaceMapperWithDefaultMethod() throws IOException {
        SourceSpecs makeAvailableUserDto = java(
          readResource("fixtures/replaceMapperWithDefaultMethod/context/UserDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserDto.java")
        );

        SourceSpecs makeAvailableUserEntity = java(
          readResource("fixtures/replaceMapperWithDefaultMethod/context/UserEntity.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserEntity.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readResource("fixtures/replaceMapperWithDefaultMethod/context/UserMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/UserMapperImpl.java")
        );

        rewriteRun(
          makeAvailableUserDto,
          makeAvailableUserEntity,
          makeAvailableGeneratedClass,
          java(
            readResource("fixtures/replaceMapperWithDefaultMethod/before/UserMapper.java"),
            readResource("fixtures/replaceMapperWithDefaultMethod/after/UserMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserMapper.java")
          )
        );
    }

    @DocumentExample
    @Test
    void replaceMapperWithConstructor() throws IOException {
        SourceSpecs makeAvailableProductDto = java(
          readResource("fixtures/replaceMapperWithConstructor/context/ProductDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/ProductDto.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readResource("fixtures/replaceMapperWithConstructor/context/ProductMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/ProductMapperImpl.java")
        );

        rewriteRun(
          makeAvailableProductDto,
          makeAvailableGeneratedClass,
          java(
            readResource("fixtures/replaceMapperWithConstructor/before/ProductMapper.java"),
            readResource("fixtures/replaceMapperWithConstructor/after/ProductMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/ProductMapper.java")
          )
        );
    }

    @DocumentExample
    @Test
    void replaceMapperWithStaticField() throws IOException {
        SourceSpecs makeAvailableOrderDto = java(
          readResource("fixtures/replaceMapperWithStaticField/context/OrderDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/OrderDto.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readResource("fixtures/replaceMapperWithStaticField/context/OrderMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/OrderMapperImpl.java")
        );

        rewriteRun(
          makeAvailableOrderDto,
          makeAvailableGeneratedClass,
          java(
            readResource("fixtures/replaceMapperWithStaticField/before/OrderMapper.java"),
            readResource("fixtures/replaceMapperWithStaticField/after/OrderMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/OrderMapper.java")
          )
        );
    }

    @Disabled
    @DocumentExample
    @Test
    void replaceAbstractMapperWithDecorator() throws IOException {
        SourceSpecs makeAvailableCustomerDto = java(
          readResource("fixtures/replaceAbstractMapperWithDecorator/context/CustomerDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/CustomerDto.java")
        );

        SourceSpecs makeAvailableCustomerEntity = java(
          readResource("fixtures/replaceAbstractMapperWithDecorator/context/CustomerEntity.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/CustomerEntity.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readResource("fixtures/replaceAbstractMapperWithDecorator/context/CustomerMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/CustomerMapperImpl.java")
        );

        rewriteRun(
          makeAvailableCustomerDto,
          makeAvailableCustomerEntity,
          makeAvailableGeneratedClass,
          java(
            readResource("fixtures/replaceAbstractMapperWithDecorator/before/CustomerMapper.java"),
            readResource("fixtures/replaceAbstractMapperWithDecorator/after/CustomerMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/CustomerMapper.java")
          )
        );
    }

}
