package com.santunioni.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ReplaceMapstructWithImpl is a recipe designed to refactor Mapstruct mapper interfaces.
 * <p>
 * It replaces @Mapper interfaces with their associated generated implementation. This process
 * includes copying default methods from the interface to the implementation class, managing
 * necessary imports, removing @Override annotations from methods, and renaming the generated
 * implementation class to match the original mapper interface name.
 * <p>
 * The recipe performs the following key steps:
 * 1. Identifies classes annotated with Mapstruct's @Mapper annotation.
 * 2. Locates the corresponding Mapstruct-generated implementation class (e.g., `MyMapperImpl`).
 * 3. Merges imports from the original interface into the implementation class.
 * 4. Copies default methods declared in the interface to the implementation class, adapting
 *    method modifiers as needed.
 * 5. Removes unnecessary annotations (such as @Override) from the implementation class.
 * 6. Renames the implementation class to match the original interface name and removes
 *    "implements" declarations.
 * <p>
 * This recipe assumes that the generated implementation is available on the project's file system
 * and verifies its existence during execution. If the generated implementation cannot be found,
 * an exception is raised.
 * <p>
 * It is recommended to run supplementary cleanup tools or recipes (e.g., RemoveUnusedImports)
 * following this recipe to handle any redundant imports or formatting inconsistencies introduced during the process.
 */
@NullMarked
public class ReplaceMapstructWithImpl extends Recipe {

    /**
     * Constructor for the ReplaceMapstructWithImpl class.
     * This method initializes an instance of the ReplaceMapstructWithImpl recipe.
     */
    public ReplaceMapstructWithImpl() {}

    @Override
    public String getDisplayName() {
        return "Replace Mapstruct Interface with Implementation";
    }

    @Override
    public String getDescription() {
        return "Replaces @Mapper interfaces with their generated implementation. Moves default methods to the class, copies imports, and removes @Override annotations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit originalCu, ExecutionContext ctx) {
                // 1. Identify if this is a Mapstruct Mapper interface
                boolean isMapper = originalCu.getClasses().stream()
                        .anyMatch(cd -> cd.getAllAnnotations().stream()
                                .anyMatch(a -> isMapstructMapper(a)));

                if (!isMapper) {
                    return originalCu;
                }

                J.ClassDeclaration originalInterface = originalCu.getClasses().get(0);

                try {
                    // 3. Parse the Generated Implementation
                    // We use a basic parser here. Since we are just moving AST nodes,
                    // we don't strictly need the full classpath for the *generated* file parsing
                    // if we rely on string matching for simple things like "Override".
                    J.CompilationUnit implCu = (J.CompilationUnit) getGeneratedClass(originalCu, originalInterface);
                    J.ClassDeclaration implClass = implCu.getClasses().get(0);

                    // ==========================================================
                    // STEP A: COPY IMPORTS
                    // ==========================================================
                    // We append original imports to the implementation imports.
                    // Duplicates will be handled by a subsequent "RemoveUnusedImports" recipe run.
                    List<J.Import> mergedImports = ListUtils.concatAll(implCu.getImports(), originalCu.getImports());
                    implCu = implCu.withImports(mergedImports);

                    // ==========================================================
                    // STEP B: PREPARE GENERATED METHODS (Remove @Override)
                    // ==========================================================
                    List<Statement> classStatements = new ArrayList<>();

                    for (Statement s : implClass.getBody().getStatements()) {
                        if (s instanceof J.MethodDeclaration m) {
                            // Filter out annotations that look like Override
                            List<J.Annotation> cleanedAnnotations = ListUtils.map(m.getLeadingAnnotations(), a -> {
                                if (a.getSimpleName().equals("Override") ||
                                        TypeUtils.isOfClassType(a.getType(), "java.lang.Override")) {
                                    return null;
                                }
                                return a;
                            });
                            classStatements.add(m.withLeadingAnnotations(cleanedAnnotations));
                        } else {
                            classStatements.add(s);
                        }
                    }

                    // ==========================================================
                    // STEP C: HARVEST & TRANSFORM DEFAULT METHODS
                    // ==========================================================
                    List<J.MethodDeclaration> defaultMethods = originalInterface.getBody().getStatements().stream()
                            .filter(J.MethodDeclaration.class::isInstance)
                            .map(J.MethodDeclaration.class::cast)
                            .filter(m -> m.hasModifier(J.Modifier.Type.Default))
                            .toList();

                    for (J.MethodDeclaration defMethod : defaultMethods) {
                        // 1. Remove 'default' modifier
                        List<J.Modifier> newModifiers = ListUtils.map(defMethod.getModifiers(), mod ->
                                mod.getType() == J.Modifier.Type.Default ? null : mod
                        );

                        // 2. Ensure 'public' modifier exists
                        boolean hasPublic = newModifiers.stream()
                                .anyMatch(m -> m.getType() == J.Modifier.Type.Public);

                        if (!hasPublic) {
                            // Create a public modifier.
                            // Note: In a real recipe, getting the whitespace/ID right is easier with JavaTemplate,
                            // but simpler manual construction works for bulk refactoring.
                            J.Modifier publicMod = new J.Modifier(
                                    Tree.randomId(), Space.EMPTY, Markers.EMPTY, J.Modifier.Type.Public, Collections.emptyList());
                            newModifiers = ListUtils.concat(publicMod, newModifiers);
                        }

                        // 3. Add to class statements
                        classStatements.add(defMethod.withModifiers(newModifiers));
                    }

                    // ==========================================================
                    // STEP D: FINALIZE CLASS STRUCTURE
                    // ==========================================================
                    // Update body with combined statements
                    implClass = implClass.withBody(implClass.getBody().withStatements(classStatements));

                    // Rename class: MyMapperImpl -> MyMapper
                    implClass = implClass.withName(implClass.getName().withSimpleName(originalInterface.getName().getSimpleName()));

                    // Remove "implements MyMapper"
                    implClass = implClass.withImplements(null);

                    // Replace the class in the CU
                    implCu = implCu.withClasses(List.of(implClass));

                    // Return the new CU, masquerading as the old file (preserving ID and Path)
                    return implCu
                            .withId(originalCu.getId())
                            .withSourcePath(originalCu.getSourcePath());

                } catch (Exception e) {
                    throw new RuntimeException("Failed to migrate Mapstruct Mapper: " + originalInterface.getName().getSimpleName(), e);
                }
            }

            private SourceFile getGeneratedClass(J.CompilationUnit originalCu, J.ClassDeclaration originalInterface) throws IOException {
                String className = originalInterface.getName().getSimpleName();
                String pkg = originalCu.getPackageDeclaration() != null ?
                        originalCu.getPackageDeclaration().getExpression().printTrimmed(getCursor()) : "";
                String implClassName = className + "Impl";
                Path generatedPath = getGeneratedClassPath(originalCu, pkg, implClassName);
                JavaParser parser = JavaParser.fromJavaVersion().build();
                return parser.parse(new String(Files.readAllBytes(generatedPath))).findFirst().orElseThrow();
            }

            private Path getGeneratedClassPath(J.CompilationUnit originalCu, String pkg, String implClassName) {
                // 2. Locate the generated file on disk
                // ctx is the ExecutionContext passed to visitCompilationUnit
                Path projectDir = getProjectDir(originalCu);
                System.out.println("projectDir = " + projectDir);
                Path generatedPath = projectDir.resolve("build/generated/sources/annotationProcessor/java/main")
                        .resolve(pkg.replace('.', '/'))
                        .resolve(implClassName + ".java");
                System.out.println("generatedPath = " + generatedPath);

                if (!Files.exists(generatedPath)) {
                    throw new IllegalStateException(String.format("Could not find generated annotations in %s from " +
                            "project %s" +
                            ". Did you compile?", generatedPath, projectDir));
                }
                return generatedPath;
            }

            private boolean isMapstructMapper(J.Annotation a) {
                return (a.getType() != null && TypeUtils.isOfClassType(a.getType(), "org.mapstruct.Mapper"))
                        || a.getSimpleName().equals("Mapper");
            }

            private Path getProjectDir(J.CompilationUnit originalCu) {
                Path currentPath = originalCu.getSourcePath();
                while (currentPath != null && !currentPath.endsWith("src")) {
                    System.out.println("currentPath = " + currentPath);
                    currentPath = currentPath.getParent();
                }
                System.out.println("currentPath = " + currentPath);

                if (currentPath != null && currentPath.endsWith("src")) {
                    return currentPath.getParent().toAbsolutePath();
                } else {
                    throw new IllegalStateException("Could not determine project directory from source path: " + originalCu.getSourcePath());
                }
            }
        };
    }
}
