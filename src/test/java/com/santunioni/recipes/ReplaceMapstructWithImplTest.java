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
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.DocumentExample;
import org.openrewrite.PathUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.openrewrite.java.Assertions.java;

class ReplaceMapstructWithImplTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceMapstructWithImpl())
          .parser(JavaParser.fromJavaVersion()
            .classpath("mapstruct"));
    }

    @DocumentExample
    @Test
    void replaceSimpleMapper(@TempDir Path tempDir) throws IOException {
        // Setup: Create the generated implementation file structure
        // The recipe expects: projectDir/build/generated/sources/annotationProcessor/java/main/...
        Path generatedDir = tempDir.resolve("build/generated/sources/annotationProcessor/java/main/com/example");
        Files.createDirectories(generatedDir);
        
        String generatedImpl = """
            package com.example;
            
            import com.example.User;
            import com.example.UserDto;
            import javax.annotation.processing.Generated;
            
            @Generated(
                value = "org.mapstruct.ap.MappingProcessor",
                date = "2025-01-01T00:00:00Z",
                comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
            )
            public class UserMapperImpl implements UserMapper {
            
                @Override
                public UserDto toDto(User user) {
                    if (user == null) {
                        return null;
                    }
                    
                    UserDto userDto = new UserDto();
                    userDto.setId(user.getId());
                    userDto.setName(user.getName());
                    return userDto;
                }
            }
            """;
        
        Files.writeString(generatedDir.resolve("UserMapperImpl.java"), generatedImpl);
        
        // The source file path must include "src" so the recipe can find the project directory
        String mapperPath = PathUtils.separatorsToSystem("src/main/java/com/example/UserMapper.java");
        rewriteRun(
          spec -> spec.paths(tempDir),
          java(
            """
              package com.example;
              
              import org.mapstruct.Mapper;
              
              @Mapper
              public interface UserMapper {
                  UserDto toDto(User user);
              }
              """,
            """
              package com.example;
              
              import com.example.User;
              import com.example.UserDto;
              import javax.annotation.processing.Generated;
              
              @Generated(
                  value = "org.mapstruct.ap.MappingProcessor",
                  date = "2025-01-01T00:00:00Z",
                  comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
              )
              public class UserMapper {
              
                  public UserDto toDto(User user) {
                      if (user == null) {
                          return null;
                      }
                      
                      UserDto userDto = new UserDto();
                      userDto.setId(user.getId());
                      userDto.setName(user.getName());
                      return userDto;
                  }
              }
              """,
            spec -> spec.path(mapperPath)
          )
        );
    }

    @Test
    void replaceMapperWithMultipleMethods(@TempDir Path tempDir) throws IOException {
        // Setup: Create the generated implementation file structure
        // The recipe expects: projectDir/build/generated/sources/annotationProcessor/java/main/...
        Path generatedDir = tempDir.resolve("build/generated/sources/annotationProcessor/java/main/com/example");
        Files.createDirectories(generatedDir);
        
        String generatedImpl = """
            package com.example;
            
            import com.example.Product;
            import com.example.ProductDto;
            import javax.annotation.processing.Generated;
            
            @Generated(
                value = "org.mapstruct.ap.MappingProcessor",
                date = "2025-01-01T00:00:00Z",
                comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
            )
            public class ProductMapperImpl implements ProductMapper {
            
                @Override
                public ProductDto toDto(Product product) {
                    if (product == null) {
                        return null;
                    }
                    
                    ProductDto productDto = new ProductDto();
                    productDto.setId(product.getId());
                    productDto.setName(product.getName());
                    productDto.setPrice(product.getPrice());
                    return productDto;
                }
                
                @Override
                public Product toEntity(ProductDto dto) {
                    if (dto == null) {
                        return null;
                    }
                    
                    Product product = new Product();
                    product.setId(dto.getId());
                    product.setName(dto.getName());
                    product.setPrice(dto.getPrice());
                    return product;
                }
            }
            """;
        
        Files.writeString(generatedDir.resolve("ProductMapperImpl.java"), generatedImpl);
        
        // The source file path must include "src" so the recipe can find the project directory
        String mapperPath = PathUtils.separatorsToSystem("src/main/java/com/example/ProductMapper.java");
        rewriteRun(
          spec -> spec.paths(tempDir),
          java(
            """
              package com.example;
              
              import org.mapstruct.Mapper;
              
              @Mapper
              public interface ProductMapper {
                  ProductDto toDto(Product product);
                  Product toEntity(ProductDto dto);
              }
              """,
            """
              package com.example;
              
              import com.example.Product;
              import com.example.ProductDto;
              import javax.annotation.processing.Generated;
              
              @Generated(
                  value = "org.mapstruct.ap.MappingProcessor",
                  date = "2025-01-01T00:00:00Z",
                  comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
              )
              public class ProductMapper {
              
                  public ProductDto toDto(Product product) {
                      if (product == null) {
                          return null;
                      }
                      
                      ProductDto productDto = new ProductDto();
                      productDto.setId(product.getId());
                      productDto.setName(product.getName());
                      productDto.setPrice(product.getPrice());
                      return productDto;
                  }
                  
                  public Product toEntity(ProductDto dto) {
                      if (dto == null) {
                          return null;
                      }
                      
                      Product product = new Product();
                      product.setId(dto.getId());
                      product.setName(dto.getName());
                      product.setPrice(dto.getPrice());
                      return product;
                  }
              }
              """,
            spec -> spec.path(mapperPath)
          )
        );
    }

    @Test
    void noChangeForNonMapperInterface(@TempDir Path tempDir) {
        // Test that non-mapper interfaces are not modified
        String interfacePath = PathUtils.separatorsToSystem("src/main/java/com/example/RegularInterface.java");
        rewriteRun(
          spec -> spec.paths(tempDir),
          java(
            """
              package com.example;
              
              public interface RegularInterface {
                  void doSomething();
              }
              """,
            spec -> spec.path(interfacePath)
          )
        );
    }
}
