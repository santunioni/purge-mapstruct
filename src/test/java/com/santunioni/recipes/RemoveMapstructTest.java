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
                        .classpath("mapstruct", "lombok"));
    }

    @DocumentExample
    @Test
    void shouldReplaceInterfaceMapper() throws IOException {
        SourceSpecs makeAvailableUserDto = java(
                readResource("fixtures/replaceInterfaceMapper/context/UserDto.java"),
                spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserDto.java")
        );

        SourceSpecs makeAvailableUserEntity = java(
                readResource("fixtures/replaceInterfaceMapper/context/UserEntity.java"),
                spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserEntity.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
                readResource("fixtures/replaceInterfaceMapper/context/UserMapperImpl.java"),
                spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/UserMapperImpl.java")
        );

        rewriteRun(
                makeAvailableUserDto,
                makeAvailableUserEntity,
                makeAvailableGeneratedClass,
                java(
                        readResource("fixtures/replaceInterfaceMapper/before/UserMapper.java"),
                        readResource("fixtures/replaceInterfaceMapper/after/UserMapper.java"),
                        spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserMapper.java")
                )
        );
    }

    @DocumentExample
    @Test
    void shouldReplaceAbsWithImplAndDecorator() throws IOException {
        SourceSpecs makeAvailableCustomerDto = java(
                readResource("fixtures/replaceAbsWithImplAndDecorator/context/CustomerDto.java"),
                spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/CustomerDto.java")
        );

        SourceSpecs makeAvailableCustomerEntity = java(
                readResource("fixtures/replaceAbsWithImplAndDecorator/context/CustomerEntity.java"),
                spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/CustomerEntity.java")
        );

        SourceSpecs makeAvailableGeneratedClass = java(
                readResource("fixtures/replaceAbsWithImplAndDecorator/context/CustomerMapperImpl.java"),
                spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/CustomerMapperImpl.java")
        );

        rewriteRun(
                makeAvailableCustomerDto,
                makeAvailableCustomerEntity,
                makeAvailableGeneratedClass,
                java(
                        readResource("fixtures/replaceAbsWithImplAndDecorator/before/CustomerMapper.java"),
                        readResource("fixtures/replaceAbsWithImplAndDecorator/after/CustomerMapper.java"),
                        spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/CustomerMapper.java")
                )
        );
    }


}
