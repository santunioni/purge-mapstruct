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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

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
    void replaceSimpleDtoMapper() {
        SourceSpecs makeAvailableSimpleDtoIn = java(

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

        // Include the generated implementation class as a source file (simulating gradle plugin including it in context)
        SourceSpecs makeAvailableGeneratedClass = java(

          """
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
            }
            """,
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapperImpl.java")
        );

        // Act - Assert
        rewriteRun(
          makeAvailableSimpleDtoIn,
          makeAvailableSimpleDtoOut,
          makeAvailableGeneratedClass,
          java(

            """
              package com.santunioni.fixtures.dtoMappers;
              
              import org.mapstruct.Mapper;
              
              @Mapper
              public interface SimpleDtoMapper {
                  SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn);
                  SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut);
              }
              """,

            "package com.santunioni.fixtures.dtoMappers;\n" +
              "\n" +
              "import org.mapstruct.Mapper;\n" +
              "\n" +
              "\n" +
              "public class SimpleDtoMapper {\n" +
              "\n" +
              "    \n" +
              "    public SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn) {\n" +
              "        if (simpleDtoIn == null) {\n" +
              "            return null;\n" +
              "        }\n" +
              "\n" +
              "        Long id = simpleDtoIn.getId();\n" +
              "        String name = simpleDtoIn.getName();\n" +
              "\n" +
              "        return new SimpleDtoOut(id, name);\n" +
              "    }\n" +
              "\n" +
              "    \n" +
              "    public SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut) {\n" +
              "        if (simpleDtoOut == null) {\n" +
              "            return null;\n" +
              "        }\n" +
              "\n" +
              "        Long id = simpleDtoOut.getId();\n" +
              "        String name = simpleDtoOut.getName();\n" +
              "\n" +
              "        return new SimpleDtoIn(id, name);\n" +
              "    }\n" +
              "}",
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/SimpleDtoMapper.java")
          )
        );
    }

    @Test
    void replaceMapperWithDefaultMethod() {
        SourceSpecs makeAvailableUserDto = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            public class UserDto {
                private final String firstName;
                private final String lastName;
            
                public UserDto(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
            
                public String getFirstName() {
                    return firstName;
                }
            
                public String getLastName() {
                    return lastName;
                }
            }
            """,
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserDto.java")
        );
        SourceSpecs makeAvailableUserEntity = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            public class UserEntity {
                private final String fullName;
            
                public UserEntity(String fullName) {
                    this.fullName = fullName;
                }
            
                public String getFullName() {
                    return fullName;
                }
            }
            """,
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserEntity.java")
        );

        // Include the generated implementation class that includes the default method implementation
        SourceSpecs makeAvailableGeneratedClass = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            import javax.annotation.processing.Generated;
            
            @Generated(
                value = "org.mapstruct.ap.MappingProcessor",
                date = "2025-01-01T00:00:00Z",
                comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
            )
            public class UserMapperImpl implements UserMapper {
            
                @Override
                public UserEntity toUserEntity(UserDto userDto) {
                    if (userDto == null) {
                        return null;
                    }
            
                    String fullName = formatFullName(userDto.getFirstName(), userDto.getLastName());
                    return new UserEntity(fullName);
                }
            }
            """,
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/UserMapperImpl.java")
        );

        // Act - Assert
        rewriteRun(
          makeAvailableUserDto,
          makeAvailableUserEntity,
          makeAvailableGeneratedClass,
          // The mapper interface with default method (before) should be replaced with the implementation class (after)
          java(
            // language=java
            """
              package com.santunioni.fixtures.dtoMappers;
              
              import org.mapstruct.Mapper;
              
              @Mapper
              public interface UserMapper {
                  UserEntity toUserEntity(UserDto userDto);
              
                  default String formatFullName(String firstName, String lastName) {              
                      return firstName + " " + lastName;
                  }
              
                  static String formatFullName2(String firstName, String lastName) {              
                      return firstName + " " + lastName;
                  }
              }
              """,

            "package com.santunioni.fixtures.dtoMappers;\n" +
              "\n" +
              "import org.mapstruct.Mapper;\n" +
              "\n" +
              "\n" +
              "public class UserMapper {\n" +
              "\n" +
              "    \n" +
              "    public UserEntity toUserEntity(UserDto userDto) {\n" +
              "        if (userDto == null) {\n" +
              "            return null;\n" +
              "        }\n" +
              "\n" +
              "        String fullName = formatFullName(userDto.getFirstName(), userDto.getLastName());\n" +
              "        return new UserEntity(fullName);\n" +
              "    }\n" +
              "\n" +
              "    static String formatFullName(String firstName, String lastName) {\n" +
              "        return firstName + \" \" + lastName;\n" +
              "    }\n" +
              "\n" +
              "    static String formatFullName2(String firstName, String lastName) {\n" +
              "        return firstName + \" \" + lastName;\n" +
              "    }\n" +
              "}",
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/UserMapper.java")
          )
        );
    }

    @Test
    void replaceMapperWithConstructor() {
        SourceSpecs makeAvailableProductDto = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            public class ProductDto {
                private final String name;
            
                public ProductDto(String name) {
                    this.name = name;
                }
            
                public String getName() {
                    return name;
                }
            }
            """,
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/ProductDto.java")
        );

        // Include the generated implementation class with a constructor
        SourceSpecs makeAvailableGeneratedClass = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            import javax.annotation.processing.Generated;
            
            @Generated(
                value = "org.mapstruct.ap.MappingProcessor",
                date = "2025-01-01T00:00:00Z",
                comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
            )
            public class ProductMapperImpl implements ProductMapper {
            
                public ProductMapperImpl() {
                }
            
                @Override
                public ProductDto toProductDto(String name) {
                    return new ProductDto(name);
                }
            }
            """,
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/ProductMapperImpl.java")
        );

        // Act - Assert
        rewriteRun(
          makeAvailableProductDto,
          makeAvailableGeneratedClass,
          java(
            """
              package com.santunioni.fixtures.dtoMappers;
              
              import org.mapstruct.Mapper;
              
              @Mapper
              public interface ProductMapper {
                  ProductDto toProductDto(String name);
              }
              """,

            "package com.santunioni.fixtures.dtoMappers;\n" +
              "\n" +
              "import org.mapstruct.Mapper;\n" +
              "\n" +
              "\n" +
              "public class ProductMapper {\n" +
              "\n" +
              "    public ProductMapper() {\n" +
              "    }\n" +
              "\n" +
              "    \n" +
              "    public ProductDto toProductDto(String name) {\n" +
              "        return new ProductDto(name);\n" +
              "    }\n" +
              "}",
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/ProductMapper.java")
          )
        );
    }

    @Test
    void replaceMapperWithStaticField() {
        SourceSpecs makeAvailableOrderDto = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            public class OrderDto {
                private final String id;
            
                public OrderDto(String id) {
                    this.id = id;
                }
            
                public String getId() {
                    return id;
                }
            }
            """,
          spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/OrderDto.java")
        );

        // Include the generated implementation class
        SourceSpecs makeAvailableGeneratedClass = java(
          """
            package com.santunioni.fixtures.dtoMappers;
            
            import javax.annotation.processing.Generated;
            
            @Generated(
                value = "org.mapstruct.ap.MappingProcessor",
                date = "2025-01-01T00:00:00Z",
                comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
            )
            public class OrderMapperImpl implements OrderMapper {
            
                @Override
                public OrderDto toOrderDto(String id) {
                    return new OrderDto(id);
                }
            }
            """,
          spec -> spec.path("build/generated/annotationProcessor/main/java/com/santunioni/fixtures/dtoMappers/OrderMapperImpl.java")
        );

        // Act - Assert
        rewriteRun(
          makeAvailableOrderDto,
          makeAvailableGeneratedClass,
          java(
            """
              package com.santunioni.fixtures.dtoMappers;
              
              import org.mapstruct.Mapper;
              
              @Mapper
              public interface OrderMapper {
                  OrderDto toOrderDto(String id);
              
                  String STATUS = "PENDING";
                  final String DEFAULT_STATUS = "PENDING";
                  static final String DEFAULT_SITUATION = "PENDING";
              }
              """,

            "package com.santunioni.fixtures.dtoMappers;\n" +
              "\n" +
              "import org.mapstruct.Mapper;\n" +
              "\n" +
              "\n" +
              "public class OrderMapper {\n" +
              "\n" +
              "    \n" +
              "    public OrderDto toOrderDto(String id) {\n" +
              "        return new OrderDto(id);\n" +
              "    }\n" +
              "\n" +
              "    public static final String STATUS = \"PENDING\";\n" +
              "    public static final String DEFAULT_STATUS = \"PENDING\";\n" +
              "    public static final String DEFAULT_SITUATION = \"PENDING\";\n" +
              "}",
            spec -> spec.path("src/main/java/com/santunioni/fixtures/dtoMappers/OrderMapper.java")
          )
        );
    }
}
