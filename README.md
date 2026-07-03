# PurgeMapstruct

An OpenRewrite recipe that inlines MapStruct mapper implementations into plain Java code, giving you full ownership of
your mapping logic.

> **New to OpenRewrite?** OpenRewrite is an automated refactoring engine for Java (and other languages). It works by
> parsing your source files into a lossless syntax tree, applying recipes that modify that tree, and writing the changes
> back — preserving formatting and comments. Before diving into this README, skim
> the [OpenRewrite introduction](https://docs.openrewrite.org/) and the plugin setup
> for [Gradle](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
> or [Maven](https://docs.openrewrite.org/reference/rewrite-maven-plugin).
> The [recipe catalog](https://docs.openrewrite.org/recipes) is also worth bookmarking.

---

## Philosophy

> *"Software should be designed for ease of reading, not ease of writing."*
> — John Ousterhout, [A Philosophy of Software Design](https://web.stanford.edu/~ouster/cgi-bin/book.php)

In any long-lived codebase, a given piece of code is read hundreds of times for every one time it is written. A
developer traces through a mapper to understand a subtle bug, a new team member reads it to figure out what a field
means, a reviewer reads it to assess correctness, an on-call engineer reads it at 2 AM to understand why production
data is wrong. Writing is a one-time cost. Reading is a recurring cost paid by everyone, forever.

This asymmetry has only sharpened. AI tools have made *writing* code cheaper than ever. The cost of producing a few
extra lines is now negligible. The cost of reading, understanding, and reasoning about code is still paid in full,
every time, by every human who touches it. Optimising for writing speed is optimising for the wrong variable.

MapStruct is a tool that optimises squarely for writing. You write less code — an interface with annotations instead
of an implementation — and MapStruct writes the rest. The problem is what this trades away.

**Silent bugs through unknown unknowns.** Ousterhout identifies *unknown unknowns* as the most dangerous form of
complexity: "there is something you need to know, but there is no way for you to find out what it is, or even whether
there is an issue. You won't find out about it until bugs appear after you make a change." MapStruct's auto-mapping by
field name is a factory for unknown unknowns. A field added to a source type silently produces `null` in the mapped
output. A rename on one side silently breaks the match on the other. These failures are invisible at compile time and
may be invisible in tests — the compiler sees nothing wrong, the field is just quietly left out. The bug waits in
production.

**Obscurity through indirection.** When mapping logic lives in annotation attributes on an interface — strings that
reference field names, expression snippets, and custom qualifier types — the actual behaviour is only visible in the
generated file, which lives in `build/` and is never committed. Understanding what a mapping does requires reading
both the interface *and* the generated implementation. That is not encapsulation; it is obscurity. Obscurity, as
Ousterhout puts it, is one of the two root causes of all software complexity, and it accumulates silently, one
annotation at a time.

**Strategic vs. tactical.** Using MapStruct to avoid writing mapping code is a tactical choice: it finishes the
current task faster but adds complexity that must be paid for, with interest, by everyone who reads the code
afterwards. The strategic choice is to write the mapping explicitly, in plain Java, where it can be read without
tools, understood without generated files, and modified with compiler support. It costs a few more minutes today; it
saves hours across the life of the codebase.

This recipe removes MapStruct from your project by replacing every `@Mapper` interface with its generated
implementation, renamed back to the original interface name. The output code will not be pretty — generated code never
is. But it will be yours to read, understand, and improve. A little ugly and obvious beats elegant and obscure.

**Start with the naive approach.** Run the recipe as-is, look at the diff, make sure your project compiles and your
tests pass. This builds intuition for what the recipe does and surfaces any edge cases specific to your codebase before
you invest in the full cleanup pipeline. Once you are confident the inlining is correct, revert and redo it with the
smart approach to produce clean, readable code before your final commit.

---

## How to use naively

### Step 1: install the OpenRewrite plugin

**Gradle** — add to your root `build.gradle`:

```groovy
plugins {
    id "org.openrewrite.rewrite" version "latest.release"
}
```

> Find the latest version on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.openrewrite.rewrite). Full
> configuration reference: [Gradle plugin docs](https://docs.openrewrite.org/reference/gradle-plugin-configuration).

**Maven** — add to your root `pom.xml`:

```xml

<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>LATEST</version>
</plugin>
```

> Find the latest version
> on [Maven Central](https://central.sonatype.com/artifact/org.openrewrite.maven/rewrite-maven-plugin). Full configuration
> reference: [Maven plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin).

### Step 2: redirect generated sources out of `build/`

Both the Gradle and Maven OpenRewrite plugins deliberately exclude the `build/` (or `target/`) directory from scanning.
This means that if MapStruct writes its `*Impl` classes into `build/generated/sources/annotationProcessor/` (the
default), the recipe cannot see them and will do nothing.

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
                    <goals>
                        <goal>add-source</goal>
                    </goals>
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
    activeRecipe("io.github.santunioni.recipes.PurgeMapstruct")
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
            <recipe>io.github.santunioni.recipes.PurgeMapstruct</recipe>
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

The sequence matters. You must compile first so that MapStruct generates the `*Impl` files, then run the recipe, then
compile again to verify the output.

**Gradle:**

```bash
./gradlew compileJava compileTestJava \
  && ./gradlew rewriteRun \
  && ./gradlew compileJava compileTestJava test
```

**Maven:**

```bash
./mvnw compile test-compile \
  && ./mvnw rewrite:run \
  && ./mvnw compile test-compile test
```

If the build and tests pass, congratulations — you have working plain Java code with no MapStruct dependency. Take a
moment to read the diff. The generated code is ugly, but it is yours now: no hidden annotation magic, no field-name
matching, no silent nulls.

Now **revert everything** (`git checkout .`) and do it again using the smart approach below. The naive run was just a
rehearsal to build confidence. Your actual commit should go through the full cleanup pipeline so the code you ship is
something your team can actually read and maintain.

> **Note on Mockito `@Spy`:** If your tests spy on mapper fields using `when(myMapper.someMethod(...)).thenReturn(...)`,
> those stubs will break after inlining because the mapper is now a concrete class and Mockito will invoke the real method
> during stubbing setup. The recipe automatically rewrites those stubs to `doReturn(...).when(myMapper).someMethod(...)`,
> which is the correct pattern for concrete-class spies.

---

## How to use smartly

The code produced by MapStruct's annotation processor is functional but mechanically ugly. Typical problems include:

- Variables declared and assigned separately when a single declaration would do
- Local variables that are only ever returned on the very next line
- Unnecessary parentheses and verbose expressions
- Imported types that are no longer used after inlining

OpenRewrite has a large [recipe catalog](https://docs.openrewrite.org/recipes) with many recipes that fix these patterns
automatically. Run them after `PurgeMapstruct` before committing.

### Step 1: add cleanup recipe dependencies

**Gradle** (`build.gradle`):

```groovy
dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:latest.release"
    rewrite "org.openrewrite.recipe:rewrite-static-analysis:latest.release"
}

rewrite {
    activeRecipe("io.github.santunioni.recipes.PurgeMapstruct")
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
            <recipe>io.github.santunioni.recipes.PurgeMapstruct</recipe>
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

| Recipe                                                            | What it fixes                                                                                                                | Docs                                                                                         |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `org.openrewrite.staticanalysis.CodeCleanup`                      | Composite: unnecessary parentheses, empty blocks, import ordering, and more                                                  | [link](https://docs.openrewrite.org/recipes/staticanalysis/codecleanup)                      |
| `org.openrewrite.staticanalysis.CommonStaticAnalysis`             | Broader composite: includes `InlineVariable`, `LambdaBlockToExpression`, `ReplaceLambdaWithMethodReference`, and dozens more | [link](https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis)             |
| `org.openrewrite.staticanalysis.InlineVariable`                   | Removes variables that exist only to be returned or thrown on the very next line                                             | [link](https://docs.openrewrite.org/recipes/staticanalysis/inlinevariable)                   |
| `org.openrewrite.staticanalysis.RemoveUnusedLocalVariables`       | Removes local variables that are never read                                                                                  | [link](https://docs.openrewrite.org/recipes/staticanalysis/removeunusedlocalvariables)       |
| `org.openrewrite.java.RemoveUnusedImports`                        | Drops imports no longer referenced after inlining                                                                            | [link](https://docs.openrewrite.org/recipes/java/removeunusedimports)                        |
| `org.openrewrite.java.OrderImports`                               | Sorts and groups import statements                                                                                           | [link](https://docs.openrewrite.org/recipes/java/orderimports)                               |
| `org.openrewrite.staticanalysis.UnnecessaryParentheses`           | Removes redundant parentheses                                                                                                | [link](https://docs.openrewrite.org/recipes/staticanalysis/unnecessaryparentheses)           |
| `org.openrewrite.staticanalysis.LambdaBlockToExpression`          | Collapses single-statement lambda blocks to expressions                                                                      | [link](https://docs.openrewrite.org/recipes/staticanalysis/lambdablocktoexpression)          |
| `org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference` | Replaces `x -> foo(x)` with `Foo::foo` where applicable                                                                      | [link](https://docs.openrewrite.org/recipes/staticanalysis/replacelambdawithmethodreference) |

**Spring-specific** — add `org.openrewrite.recipe:rewrite-spring` to your `rewrite` dependencies to use these:

| Recipe                                                                    | What it fixes                                                                                                                                                                                                                                              | Docs                                                                                                          |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `io.moderne.java.spring.boot.FieldToConstructorInjection`                 | Converts `@Autowired` field injection to constructor injection; marks fields `final`; moves `@Qualifier` to parameters. Generated MapStruct `*Impl` code relies heavily on field injection — reach for this first.                                          | [link](https://docs.moderne.io/user-documentation/recipes/recipe-catalog/java/spring/boot/fieldtoconstructorinjection) |
| `org.openrewrite.java.spring.NoAutowiredOnConstructor`                    | Removes the redundant `@Autowired` annotation from single-constructor beans where Spring can infer injection automatically                                                                                                                                   | [link](https://docs.openrewrite.org/recipes/java/spring/noautowiredonconstructor)                             |
| `org.openrewrite.java.spring.framework.BeanMethodsNotPublic`              | Removes the unnecessary `public` modifier from `@Bean` methods — Spring does not require it and generated code often has it                                                                                                                                 | [link](https://docs.openrewrite.org/recipes/java/spring/framework/beanmethodsnotpublic)                       |
| `org.openrewrite.java.spring.NoRequestMappingAnnotation`                  | Replaces verbose `@RequestMapping(method = GET)` with the dedicated shorthand annotations (`@GetMapping`, `@PostMapping`, etc.)                                                                                                                             | [link](https://docs.openrewrite.org/recipes/java/spring/norequestmappingannotation)                           |
| `org.openrewrite.java.spring.boot3.PreciseBeanType`                       | Changes `@Bean` methods that declare a return type of an interface or abstract class to return the concrete type, making the bean graph easier to reason about                                                                                              | [link](https://docs.openrewrite.org/recipes/java/spring/boot3/precisebeantype)                                |
| `org.openrewrite.java.spring.NoRepoAnnotationOnRepoInterface`             | Removes redundant `@Repository` annotations from Spring Data `Repository` sub-interfaces — Spring Data already registers them                                                                                                                               | [link](https://docs.openrewrite.org/recipes/java/spring/norepoannotationonrepointerface)                      |
| `org.openrewrite.java.spring.boot3.SpringBoot3BestPracticesOnly`          | Composite: normalise properties to kebab-case, enable virtual threads, remove `public` from `@Bean` methods, precise bean types, Spring security best practices — all without touching your Spring Boot version                                              | [link](https://docs.openrewrite.org/recipes/java/spring/boot3/springboot3bestpracticesonly)                   |

> **Note:** `FieldToConstructorInjection` is part of Moderne's extended recipe set (`io.moderne.recipe:rewrite-spring`).
> Add it alongside the standard `org.openrewrite.recipe:rewrite-spring` dependency:
> ```groovy
> dependencies {
>     rewrite "org.openrewrite.recipe:rewrite-spring:latest.release"
>     rewrite "io.moderne.recipe:rewrite-spring:latest.release"
> }
> ```

Browse the full catalog at [docs.openrewrite.org/recipes](https://docs.openrewrite.org/recipes) to find recipes suited
to your framework and Java version.

### Step 2: use a formatter, not a linter

After the recipes run, the code will be syntactically correct but may not match your team's formatting conventions. Use
**Spotless** to apply formatting automatically.

> **Avoid Checkstyle** in your codebase altogether. Checkstyle reports violations but cannot fix them, which means
> someone has to fix them manually. No one should be formatting files by hand. Use Spotless instead: it
> automatically applies all rules it enforces..

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

Run with `./gradlew spotlessApply`.
See [Spotless Gradle docs](https://github.com/diffplug/spotless/tree/main/plugin-gradle) for all formatter options.

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

Run with `./mvnw spotless:apply`. See [Spotless Maven docs](https://github.com/diffplug/spotless/tree/main/plugin-maven)
for all formatter options.

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

Running the quality recipes and formatter both before the inlining and after is even better: a cleaner codebase going in
means cleaner output coming out.

> **Minimising the diff footprint of your PR.**
> The cleanup recipes and formatter will touch many files across your codebase — files that have nothing to do with MapStruct.
> If you bundle all of that into the same PR as the inlining, reviewers will struggle to tell which changes are structural
> (the purge) and which are cosmetic (the cleanup).
> To keep the purge PR focused and reviewable, run the auto-refactors first — without the purge — commit and ship that as a
> separate PR, then come back and run the purge on its own. The inlining diff will be much smaller and easier to reason about.

---

## Feedback

Tried the recipe? We'd love to hear from you — [open an issue](https://github.com/santunioni/purge-mapstruct/issues/new) on GitHub.

Some things worth sharing:

- **Were the instructions clear?** If a step confused you or required extra research, let us know so we can improve the guide.
- **Did you hit any problems?** Edge cases in generated code, unexpected compilation errors after the rewrite, or anything the recipe didn't handle — file an issue with a minimal reproducer and we'll look into it.
- **How was the generated code quality after running the smart approach?** Did the cleanup recipes and formatter leave you with readable, maintainable code? Or are there patterns they still miss?

Your experience — good or bad — directly shapes what gets fixed and prioritised next.

---

## Further reading

- [OpenRewrite documentation](https://docs.openrewrite.org/)
- [OpenRewrite recipe catalog](https://docs.openrewrite.org/recipes)
- [Gradle plugin configuration](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
- [Maven plugin configuration](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
- [Spotless Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [Spotless Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven)
