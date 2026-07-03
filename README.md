# PurgeMapstruct

An OpenRewrite recipe that inlines MapStruct mapper implementations into plain Java code, giving you full ownership of your mapping logic.

## Philosophy

MapStruct's greatest feature is its greatest flaw: it auto-maps your value objects by matching field names. When it can't match, you either get a silently unmapped field (null in production) or you write the mapping logic as annotation attributes — strings that bypass the compiler and lose type safety.

The result is a codebase where mappings are invisible, bugs are silent, and understanding what actually happens requires reading both the interface and the generated implementation. MapStruct optimises for writing less code, not for reading or maintaining it.

This recipe removes MapStruct from your project by replacing every `@Mapper` interface with its generated implementation, renamed back to the original interface name. The output code will not be pretty — generated code never is. But it will be yours to read, understand, and improve.

Don't commit right away. First make sure the project compiles and your tests pass. Then read the rest of this document to learn how to automatically clean up the generated code before committing.

---

## How to use naively

### Step 1: redirect generated sources out of `build/`

Both the Gradle and Maven OpenRewrite plugins deliberately exclude the `build/` (or `target/`) directory from scanning. This means that if MapStruct writes its `*Impl` classes into `build/generated/sources/annotationProcessor/` (the default), the recipe cannot see them and will do nothing.

You need to redirect annotation processor output to a source directory inside `src/` before running the recipe.

**Gradle** (`build.gradle` or root `build.gradle`):

```groovy
tasks.withType(JavaCompile).configureEach {
    options.generatedSourceOutputDirectory = file("$projectDir/src/generated/java")
}

sourceSets {
    main {
        java.srcDirs += "$projectDir/src/generated/java"
    }
}
```

Add `src/generated/` to `.gitignore` (it is still build output, just placed where OpenRewrite can reach it).

**Maven** (`pom.xml`):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <generatedSourcesDirectory>${project.basedir}/src/generated/java</generatedSourcesDirectory>
    </configuration>
</plugin>

<build>
    <sourceDirectory>src/main/java</sourceDirectory>
    <!-- tell Maven the generated sources are also part of the compile source root -->
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>add-generated-sources</id>
                    <phase>generate-sources</phase>
                    <goals><goal>add-source</goal></goals>
                    <configuration>
                        <sources>
                            <source>${project.basedir}/src/generated/java</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Step 2: add the recipe as a dependency

**Gradle** (`build.gradle`):

```groovy
plugins {
    id "org.openrewrite.rewrite" version "latest.release"
}

dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:latest.release"
}

rewrite {
    activeRecipe("com.santunioni.recipes.PurgeMapstruct")
}
```

**Maven** (`pom.xml`):

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>LATEST</version>
    <configuration>
        <activeRecipes>
            <recipe>com.santunioni.recipes.PurgeMapstruct</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.santunioni</groupId>
            <artifactId>purge-mapstruct</artifactId>
            <version>LATEST</version>
        </dependency>
    </dependencies>
</plugin>
```

### Step 3: run the recipe

The sequence matters. You must compile first so that MapStruct generates the `*Impl` files, then run the recipe, then compile again to verify the output.

**Gradle:**

```bash
./gradlew compileJava compileTestJava \
  && ./gradlew rewriteRun \
  && ./gradlew compileJava compileTestJava test
```

**Maven:**

```bash
mvn compile test-compile \
  && mvn rewrite:run \
  && mvn compile test-compile test
```

If the build and tests pass, you have working plain Java code. The `*Impl` source files under `src/generated/` have been deleted by the recipe (they were merged into the original mapper files). You can remove the `src/generated/` source root configuration from your build file — it is no longer needed.

> **Note on Mockito `@Spy`:** If your tests spy on mapper fields using `when(myMapper.someMethod(...)).thenReturn(...)`, those stubs will break after inlining because the mapper is now a concrete class and Mockito will invoke the real method during stubbing setup. The recipe automatically rewrites those stubs to `doReturn(...).when(myMapper).someMethod(...)`, which is the correct pattern for concrete-class spies.

---

## How to use smartly

The code produced by MapStruct's annotation processor is functional but mechanically ugly. Typical problems include:

- Variables declared and assigned separately when a single declaration would do
- Local variables that are only ever returned on the very next line
- Unnecessary parentheses and verbose expressions
- Missing `@Override` cleanup
- Imported types that are no longer used

OpenRewrite has a large catalog of recipes that fix these patterns automatically. Run them **before** committing the inlined code.

### Recommended OpenRewrite recipes

Add `rewrite-static-analysis` to your rewrite dependencies:

**Gradle:**
```groovy
dependencies {
    rewrite "org.openrewrite.recipe:rewrite-static-analysis:latest.release"
}
```

**Maven:**
```xml
<dependency>
    <groupId>org.openrewrite.recipe</groupId>
    <artifactId>rewrite-static-analysis</artifactId>
    <version>LATEST</version>
</dependency>
```

Then activate the following recipes (run them together or individually):

| Recipe | What it fixes |
|---|---|
| `org.openrewrite.staticanalysis.CodeCleanup` | Composite: removes unnecessary parentheses, empty blocks, unused labels, pads format, orders imports |
| `org.openrewrite.staticanalysis.CommonStaticAnalysis` | Broader composite: includes `InlineVariable`, `LambdaBlockToExpression`, `ReplaceLambdaWithMethodReference`, and many more |
| `org.openrewrite.staticanalysis.InlineVariable` | Removes variables that exist only to be returned or thrown on the very next line |
| `org.openrewrite.staticanalysis.RemoveUnusedLocalVariables` | Removes local variables that are never read |
| `org.openrewrite.java.RemoveUnusedImports` | Drops imports that are no longer referenced after inlining |
| `org.openrewrite.java.OrderImports` | Sorts and groups import statements |
| `org.openrewrite.staticanalysis.UnnecessaryParentheses` | Removes redundant parentheses in expressions |
| `org.openrewrite.staticanalysis.LambdaBlockToExpression` | Collapses single-statement lambda blocks to expressions |
| `org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference` | Replaces `x -> foo(x)` with `Foo::foo` where applicable |
| `org.openrewrite.java.migrate.lang.JavaBestPractices` | `var`, modern API usage, and other Java idiom improvements |

### Use a formatter, not a linter

After the recipe runs, the code will be syntactically correct but may not match your team's formatting conventions. Use **Spotless** to apply formatting automatically:

**Gradle:**
```groovy
plugins {
    id "com.diffplug.spotless" version "latest.release"
}

spotless {
    java {
        googleJavaFormat() // or palantirJavaFormat(), eclipse(), etc.
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

Run it with `./gradlew spotlessApply`.

**Maven:**
```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>LATEST</version>
    <configuration>
        <java>
            <googleJavaFormat/>
            <removeUnusedImports/>
            <trimTrailingWhitespace/>
            <endWithNewline/>
        </java>
    </configuration>
</plugin>
```

Run it with `mvn spotless:apply`.

> **Avoid Checkstyle** for this step. Checkstyle reports violations but cannot fix them. After inlining hundreds of mappers, you do not want a list of findings — you want the fixes applied automatically. Spotless applies the formatter and moves on.

### Recommended full workflow

```
1. Configure generated sources to go to src/generated/java
2. Compile (generates MapStruct *Impl files)
3. Run PurgeMapstruct recipe (inlines impls, deletes *Impl files)
4. Run CodeCleanup / CommonStaticAnalysis recipes
5. Run Spotless apply
6. Compile + test (verify everything passes)
7. Remove the src/generated/ source root from your build config
8. Commit
```

Running the quality recipes and formatter both before and after the inlining is even better: a cleaner codebase before inlining means cleaner generated output, and running them after catches anything the inlining introduced.
