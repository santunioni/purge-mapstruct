package com.santunioni.recipes;


import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ReplaceMapstructWithImpl is a recipe designed to refactor Mapstruct mapper interfaces.
 * <p>
 * It replaces @Mapper interfaces with their associated generated implementation. This process
 * includes managing necessary imports, removing @Override annotations from methods, and renaming
 * the generated implementation class to match the original mapper interface name.
 * <p>
 * The recipe performs the following key steps:
 * 1. Identifies classes annotated with Mapstruct's @Mapper annotation.
 * 2. Locates the corresponding Mapstruct-generated implementation class (e.g., `MyMapperImpl`).
 * 3. Merges imports from the original interface into the implementation class.
 * 4. Removes unnecessary annotations (such as @Override) from the implementation class.
 * 5. Renames the implementation class to match the original interface name and removes
 *    "implements" declarations.
 * <p>
 * This recipe assumes that the generated implementation is available on the project's file system
 * and verifies its existence during execution. If the generated implementation cannot be found,
 * an exception is raised.
 * <p>
 * Note: This recipe does not copy default methods from the interface. It only works with
 * interfaces that do not have default methods.
 * <p>
 * It is recommended to run supplementary cleanup tools or recipes (e.g., RemoveUnusedImports)
 * following this recipe to handle any redundant imports or formatting inconsistencies introduced during the process.
 */
@NullMarked
@Log
public class RemoveMapstruct extends Recipe {

    /**
     * Constructor for the ReplaceMapstructWithImpl class.
     * This method initializes an instance of the ReplaceMapstructWithImpl recipe.
     */
    public RemoveMapstruct() {}

    @Override
    public String getDisplayName() {
        return "Replace Mapstruct Interface with Implementation";
    }

    @Override
    public String getDescription() {
        return "Replaces @Mapper interfaces with their generated implementation. Copies imports and removes @Override annotations. Only supports interfaces without default methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
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
                        if (s instanceof J.MethodDeclaration) {
                            J.MethodDeclaration m = (J.MethodDeclaration) s;
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
                    // STEP C: FINALIZE CLASS STRUCTURE
                    // ==========================================================
                    // Update body with combined statements
                    implClass = implClass.withBody(implClass.getBody().withStatements(classStatements));

                    // Rename class: MyMapperImpl -> MyMapper
                    implClass = implClass.withName(implClass.getName().withSimpleName(originalInterface.getName().getSimpleName()));

                    // Remove "implements MyMapper"
                    implClass = implClass.withImplements(null);

                    // Replace the class in the CU
                    implCu = implCu.withClasses(Collections.singletonList(implClass));

                    // Return the new CU, masquerading as the old file (preserving ID and Path)
                    return implCu
                            .withId(originalCu.getId())
                            .withSourcePath(originalCu.getSourcePath());

                } catch (Exception e) {
                    log.severe("Error processing @Mapper class " + originalCu.getClasses().get(0).getName() + " ");
                    throw new RuntimeException("Failed to migrate Mapstruct Mapper: " + originalInterface.getName().getSimpleName(), e);
                }
            }

            private SourceFile getGeneratedClass(J.CompilationUnit originalCu, J.ClassDeclaration originalInterface) throws IOException {
                String className = originalInterface.getName().getSimpleName();
                if (originalCu.getPackageDeclaration() == null) {
                    throw new IllegalArgumentException("Original compilation unit must have a package declaration");
                }
                String pkg = originalCu.getPackageDeclaration().getExpression().printTrimmed(getCursor());
                String implClassName = className + "Impl";

                // Note: Generated files from MapStruct are NOT accessible through ExecutionContext.
                // ExecutionContext only contains source files being processed, not build artifacts.
                // Generated files must be read from the file system where they were created by the annotation processor.
                // This works for both real projects (where files are on disk) and tests (where files are in temp directories).
                Path resolvedPath = getGeneratedClassPath(originalCu, pkg, implClassName);

                JavaParser parser = JavaParser.fromJavaVersion().build();

                try {
                    return parser.parse(new String(Files.readAllBytes(resolvedPath))).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Could not parse generated class: " + resolvedPath));
                } catch (IllegalStateException e) {
                    log.severe("Could not parse generated class: " + resolvedPath + "\n" + e.getMessage());
                    throw e;
                }
            }

            private Path getGeneratedClassPath(J.CompilationUnit originalCu, String pkg, String implClassName) {
                // Fallback: use project directory resolution
                Path projectDir = getProjectDir(originalCu);
                Path generatedPath = projectDir.resolve(GRADLE_RELATIVE_GENERATED_SOURCES_PATH)
                        .resolve(pkg.replace('.', '/'))
                        .resolve(implClassName + ".java");

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
                Path searchPath = currentPath.toAbsolutePath();
                while (true) {
                    Path fileName = searchPath.getFileName();
                    if (fileName != null && "src".equals(fileName.toString())) {
                        return searchPath.getParent().toAbsolutePath();
                    }
                    Path next = searchPath.getParent();
                    if (next == null || next.equals(searchPath)) {
                        break;
                    }
                    searchPath = next;
                }

                throw new IllegalStateException("Could not determine project directory from source path: " + originalCu.getSourcePath());
            }
        };
    }

    String GRADLE_RELATIVE_GENERATED_SOURCES_PATH = "build/generated/sources/annotationProcessor/java/main";
}
