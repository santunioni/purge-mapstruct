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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.openrewrite.java.Assertions.java;

class RemoveMapstructTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveMapstruct())
          .parser(JavaParser.fromJavaVersion()
            .classpath("mapstruct"));
    }

    @DocumentExample
    @Test
    void replaceSimpleDtoMapper() throws IOException {
        // Given
        generateMapstructClass("com.santunioni.fixtures.dtoMappers.SimpleDtoMapperImpl", """
package com.santunioni.fixtures.dtoMappers;

import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-01-01T00:00:00Z",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
public class SimpleDtoMapperImpl implements SimpleDtoMapper {
    @Override
    public SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn) {
        if (simpleDtoIn == null) {
            return null;
        }

        Long id = simpleDtoIn.getId();
        String name = simpleDtoIn.getName();

        return new SimpleDtoOut(id, name);
    }
    @Override
    public SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut) {
        if (simpleDtoOut == null) {
            return null;
        }

        Long id = simpleDtoOut.getId();
        String name = simpleDtoOut.getName();

        return new SimpleDtoIn(id, name);
    }
}""");

        SourceSpecs makeAvailableSimpleDtoIn = java(
          // language=java
          """
            package com.santunioni.fixtures.dtoMappers;
            
            public class SimpleDtoIn {
                private final Long id;
                private final String name;
            
                public SimpleDtoIn(Long id, String name) {
                    this.id = id;
                    this.name = name;
                }
            
                public Long getId() {
                    return id;
                }
            
                public String getName() {
                    return name;
                }
            }
            """,
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoIn.java")
        );
        SourceSpecs makeAvailableSimpleDtoOut = java(
          // language=java
          """
            package com.santunioni.fixtures.dtoMappers;
            
            public class SimpleDtoOut {
                private final Long id;
                private final String name;
            
                public SimpleDtoOut(Long id, String name) {
                    this.id = id;
                    this.name = name;
                }
            
                public Long getId() {
                    return id;
                }
            
                public String getName() {
                    return name;
                }
            }
            """,
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoOut.java")
        );

        // Act - Assert
        rewriteRun(
          // Include the DTO classes so types are available
          makeAvailableSimpleDtoIn,
          makeAvailableSimpleDtoOut,
          // Test using the actual SimpleDtoMapper interface from fixtures
          // The recipe should find the generated SimpleDtoMapperImpl in build/generated/sources/annotationProcessor/java/main
          // and replace the interface with the implementation class
          java(
            // language=java
            """
 package com.santunioni.fixtures.dtoMappers;
 
 import javax.annotation.processing.Generated;
 
 import org.mapstruct.Mapper;
 
 @Generated(
     value = "org.mapstruct.ap.MappingProcessor",
     date = "2025-01-01T00:00:00Z",
     comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
 )
 public class SimpleDtoMapper {
 
     public SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn) {
         if (simpleDtoIn == null) {
             return null;
         }
 
         Long id = simpleDtoIn.getId();
         String name = simpleDtoIn.getName();
 
         return new SimpleDtoOut(id, name);
     }
 
     public SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut) {
         if (simpleDtoOut == null) {
             return null;
         }
 
         Long id = simpleDtoOut.getId();
         String name = simpleDtoOut.getName();
 
         return new SimpleDtoIn(id, name);
     }
 }""",
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapper.java")
          )
        );
    }

    private static void generateMapstructClass(String classFqn, @Language("java") String generatedImpl) throws IOException {
        // Create the generated implementation file that MapStruct would generate
        // This simulates the annotation processor output
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path classPath = Path.of(classFqn.replace('.', '/'));
        Path generatedDir = projectRoot.resolve("build/generated/sources/annotationProcessor/java/main/").resolve(classPath.getParent());
        Files.createDirectories(generatedDir);

        Files.writeString(generatedDir.resolve(Arrays.stream(classFqn.split("\\.")).toList().getLast() + ".java"), generatedImpl);
    }
}
