# PurgeMapstruct

An OpenRewrite recipe that inlines MapStruct mapper implementations into plain Java code, giving you full ownership of your mapping logic.

> **New to OpenRewrite?** OpenRewrite is an automated refactoring engine for Java (and other languages). It works by parsing your source files into a lossless syntax tree, applying recipes that modify that tree, and writing the changes back — preserving formatting and comments. Before diving into this README, skim the [OpenRewrite introduction](https://docs.openrewrite.org/) and the plugin setup for [Gradle](https://docs.openrewrite.org/reference/gradle-plugin-configuration) or [Maven](https://docs.openrewrite.org/reference/rewrite-maven-plugin). The [recipe catalog](https://docs.openrewrite.org/recipes) is also worth bookmarking.

---

## Philosophy

MapStruct's greatest feature is its greatest flaw: it auto-maps your value objects by matching field names. When it can't match, you either get a silently unmapped field (null in production) or you write the mapping logic as annotation attributes — strings that bypass the compiler and lose type safety.

The result is a codebase where mappings are invisible, bugs are silent, and understanding what actually happens requires reading both the interface and the generated implementation. MapStruct optimises for writing less code, not for reading or maintaining it.

This recipe removes MapStruct from your project by replacing every `@Mapper` interface with its generated implementation, renamed back to the original interface name. The output code will not be pretty — generated code never is. But it will be yours to read, understand, and improve.

**Start with the naive approach.** Run the recipe as-is, look at the diff, make sure your project compiles and your tests pass. This builds intuition for what the recipe does and surfaces any edge cases specific to your codebase before you invest in the full cleanup pipeline. Once you are confident the inlining is correct, revert and redo it with the smart approach to produce clean, readable code before your final commit.

---

## How to use naively

### Step 1: install the OpenRewrite plugin

**Gradle** — add to your root `build.gradle`:

```groovy
plugins {
    id "org.openrewrite.rewrite" version "latest.release"
}
```

> Find the latest version on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.openrewrite.rewrite). Full configuration reference: [Gradle plugin docs](https://docs.openrewrite.org/reference/gradle-plugin-configuration).

**Maven** — add to your root `pom.xml`:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>LATEST</version>
</plugin>
```

> Find the latest version on [Maven Central](https://central.sonatype.com/artifact/org.openrewrite.maven/rewrite-maven-plugin). Full configuration reference: [Maven plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin).

### Step 2: redirect generated sources out of `build/`

Both the Gradle and Maven OpenRewrite plugins deliberately exclude the `build/` (or `target/`) directory from scanning. This means that if MapStruct writes its `*Impl` classes into `build/generated/sources/annotationProcessor/` (the default), the recipe cannot see them and will do nothing.

You need to redirect annotation processor output to a source directory inside `src/` before running the recipe.

**Gradle** (root `build.gradle`, applied to all subprojects):

```groovy
subprojects {
    tasks.withType(JavaCompile).configureEach {
        options.generatedSourceOutputDirectory = file("$projectDir/src/generated/java")
    }

    sourceSets {
        main {
            java.srcDirs += "$projectDir/src/generated/java"
        }
    }
}
```

**Maven** (`pom.xml`):

```xml
<build>
    <plugins>
        <!-- redirect annotation processor output -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <generatedSourcesDirectory>${project.basedir}/src/generated/java</generatedSourcesDirectory>
            </configuration>
        </plugin>

        <!-- register the directory as a source root -->
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

### Step 3: add PurgeMapstruct as a recipe dependency

**Gradle** (`build.gradle`):

```groovy
dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:latest.release"
}

rewrite {
    activeRecipe("com.santunioni.recipes.PurgeMapstruct")
}
```

**Maven** (`pom.xml`, inside the `rewrite-maven-plugin` configuration):

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

### Step 4: run the recipe

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

If the build and tests pass, congratulations — you have working plain Java code with no MapStruct dependency. Take a moment to read the diff. The generated code is ugly, but it is yours now: no hidden annotation magic, no field-name matching, no silent nulls.

Now **revert everything** (`git checkout .`) and do it again using the smart approach below. The naive run was just a rehearsal to build confidence. Your actual commit should go through the full cleanup pipeline so the code you ship is something your team can actually read and maintain.

> **Note on Mockito `@Spy`:** If your tests spy on mapper fields using `when(myMapper.someMethod(...)).thenReturn(...)`, those stubs will break after inlining because the mapper is now a concrete class and Mockito will invoke the real method during stubbing setup. The recipe automatically rewrites those stubs to `doReturn(...).when(myMapper).someMethod(...)`, which is the correct pattern for concrete-class spies.

---

## How to use smartly

The code produced by MapStruct's annotation processor is functional but mechanically ugly. Typical problems include:

- Variables declared and assigned separately when a single declaration would do
- Local variables that are only ever returned on the very next line
- Unnecessary parentheses and verbose expressions
- Imported types that are no longer used after inlining

OpenRewrite has a large [recipe catalog](https://docs.openrewrite.org/recipes) with many recipes that fix these patterns automatically. Run them after `PurgeMapstruct` before committing.

### Step 1: add cleanup recipe dependencies

**Gradle** (`build.gradle`):

```groovy
dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:latest.release"
    rewrite "org.openrewrite.recipe:rewrite-static-analysis:latest.release"
}

rewrite {
    activeRecipe("com.santunioni.recipes.PurgeMapstruct")
    activeRecipe("org.openrewrite.staticanalysis.CodeCleanup")
    activeRecipe("org.openrewrite.staticanalysis.CommonStaticAnalysis")
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
            <recipe>org.openrewrite.staticanalysis.CodeCleanup</recipe>
            <recipe>org.openrewrite.staticanalysis.CommonStaticAnalysis</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.santunioni</groupId>
            <artifactId>purge-mapstruct</artifactId>
            <version>LATEST</version>
        </dependency>
        <dependency>
            <groupId>org.openrewrite.recipe</groupId>
            <artifactId>rewrite-static-analysis</artifactId>
            <version>LATEST</version>
        </dependency>
    </dependencies>
</plugin>
```

### Recommended recipes

| Recipe | What it fixes | Docs |
|---|---|---|
| `org.openrewrite.staticanalysis.CodeCleanup` | Composite: unnecessary parentheses, empty blocks, import ordering, and more | [link](https://docs.openrewrite.org/recipes/staticanalysis/codecleanup) |
| `org.openrewrite.staticanalysis.CommonStaticAnalysis` | Broader composite: includes `InlineVariable`, `LambdaBlockToExpression`, `ReplaceLambdaWithMethodReference`, and dozens more | [link](https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis) |
| `org.openrewrite.staticanalysis.InlineVariable` | Removes variables that exist only to be returned or thrown on the very next line | [link](https://docs.openrewrite.org/recipes/staticanalysis/inlinevariable) |
| `org.openrewrite.staticanalysis.RemoveUnusedLocalVariables` | Removes local variables that are never read | [link](https://docs.openrewrite.org/recipes/staticanalysis/removeunusedlocalvariables) |
| `org.openrewrite.java.RemoveUnusedImports` | Drops imports no longer referenced after inlining | [link](https://docs.openrewrite.org/recipes/java/removeunusedimports) |
| `org.openrewrite.java.OrderImports` | Sorts and groups import statements | [link](https://docs.openrewrite.org/recipes/java/orderimports) |
| `org.openrewrite.staticanalysis.UnnecessaryParentheses` | Removes redundant parentheses | [link](https://docs.openrewrite.org/recipes/staticanalysis/unnecessaryparentheses) |
| `org.openrewrite.staticanalysis.LambdaBlockToExpression` | Collapses single-statement lambda blocks to expressions | [link](https://docs.openrewrite.org/recipes/staticanalysis/lambdablocktoexpression) |
| `org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference` | Replaces `x -> foo(x)` with `Foo::foo` where applicable | [link](https://docs.openrewrite.org/recipes/staticanalysis/replacelambdawithmethodreference) |

Browse the full catalog at [docs.openrewrite.org/recipes](https://docs.openrewrite.org/recipes) to find recipes suited to your framework and Java version.

### Step 2: use a formatter, not a linter

After the recipes run, the code will be syntactically correct but may not match your team's formatting conventions. Use **Spotless** to apply formatting automatically.

> **Avoid Checkstyle** in your codebase altogether. Checkstyle reports violations but cannot fix them, which means someone has to fix them manually. No one should be formatting files by hand. Use Spotless instead: it enforces the same rules and applies them automatically.

**Gradle** (`build.gradle`):

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

Run with `./gradlew spotlessApply`. See [Spotless Gradle docs](https://github.com/diffplug/spotless/tree/main/plugin-gradle) for all formatter options.

**Maven** (`pom.xml`):

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

Run with `mvn spotless:apply`. See [Spotless Maven docs](https://github.com/diffplug/spotless/tree/main/plugin-maven) for all formatter options.

### Full workflow

```
1.  Configure generated sources to go to src/generated/java  (Step 2 above)
2.  Compile  →  MapStruct generates *Impl files
3.  [Naive run]  Run PurgeMapstruct, verify compile + tests pass, read the diff
4.  Revert  (git checkout .)
5.  [Smart run]  Run PurgeMapstruct + CodeCleanup + CommonStaticAnalysis together
6.  Run Spotless apply
7.  Compile + test  →  verify everything still passes
8.  Remove the src/generated/ source root from your build config
9.  Remove MapStruct dependencies from your build file
10. Commit
```

Running the quality recipes and formatter both before the inlining and after is even better: a cleaner codebase going in means cleaner output coming out.

---

## Further reading

- [OpenRewrite documentation](https://docs.openrewrite.org/)
- [OpenRewrite recipe catalog](https://docs.openrewrite.org/recipes)
- [Gradle plugin configuration](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
- [Maven plugin configuration](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
- [Spotless Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [Spotless Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven)
