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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.format.AutoFormat;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.openrewrite.java.Assertions.java;

class RemoveMapstructTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new RemoveMapstruct(), new AutoFormat("com.santunioni.styles.AutoFormatRecipeOutputForTest", false))
          .parser(JavaParser.fromJavaVersion()
            .classpath("mapstruct"));
    }

    private String readFixture(String testName, String fixtureType, String fileName) throws IOException {
        Path fixturePath = Paths.get("src/test/resources/com/santunioni/fixtures", testName, fixtureType, fileName);
        return Files.readString(fixturePath);
    }

    @DocumentExample
    @Test
    void replaceSimpleDtoMapper() throws IOException {
        String testName = "replaceSimpleDtoMapper";

        SourceSpecs makeAvailableSimpleDtoIn = java(
          readFixture(testName, "context", "SimpleDtoIn.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoIn.java")
        );

        SourceSpecs makeAvailableSimpleDtoOut = java(
          readFixture(testName, "context", "SimpleDtoOut.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoOut.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readFixture(testName, "context", "SimpleDtoMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapperImpl.java")
        );

        rewriteRun(
          makeAvailableSimpleDtoIn,
          makeAvailableSimpleDtoOut,
          makeAvailableGeneratedClass,
          java(
            readFixture(testName, "before", "SimpleDtoMapper.java"),
            readFixture(testName, "after", "SimpleDtoMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapper.java")
          )
        );
    }

    @Test
    void replaceMapperWithDefaultMethod() throws IOException {
        String testName = "replaceMapperWithDefaultMethod";

        SourceSpecs makeAvailableUserDto = java(
          readFixture(testName, "context", "UserDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserDto.java")
        );

        SourceSpecs makeAvailableUserEntity = java(
          readFixture(testName, "context", "UserEntity.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserEntity.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readFixture(testName, "context", "UserMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/UserMapperImpl.java")
        );

        rewriteRun(
          makeAvailableUserDto,
          makeAvailableUserEntity,
          makeAvailableGeneratedClass,
          java(
            readFixture(testName, "before", "UserMapper.java"),
            readFixture(testName, "after", "UserMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserMapper.java")
          )
        );
    }

    @Test
    void replaceMapperWithConstructor() throws IOException {
        String testName = "replaceMapperWithConstructor";

        SourceSpecs makeAvailableProductDto = java(
          readFixture(testName, "context", "ProductDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/ProductDto.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readFixture(testName, "context", "ProductMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/ProductMapperImpl.java")
        );

        rewriteRun(
          makeAvailableProductDto,
          makeAvailableGeneratedClass,
          java(
            readFixture(testName, "before", "ProductMapper.java"),
            readFixture(testName, "after", "ProductMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/ProductMapper.java")
          )
        );
    }

    @Test
    void replaceMapperWithStaticField() throws IOException {
        String testName = "replaceMapperWithStaticField";

        SourceSpecs makeAvailableOrderDto = java(
          readFixture(testName, "context", "OrderDto.java"),
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/OrderDto.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
          readFixture(testName, "context", "OrderMapperImpl.java"),
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/OrderMapperImpl.java")
        );

        rewriteRun(
          makeAvailableOrderDto,
          makeAvailableGeneratedClass,
          java(
            readFixture(testName, "before", "OrderMapper.java"),
            readFixture(testName, "after", "OrderMapper.java"),
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/OrderMapper.java")
          )
        );
    }
}
