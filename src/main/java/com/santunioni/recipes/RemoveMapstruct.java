package com.santunioni.recipes;


import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReplaceMapstructWithImpl is a recipe designed to refactor Mapstruct mapper interfaces.
 * <p>
 * It replaces @Mapper interfaces with their associated generated implementation. This process
 * includes managing necessary imports, removing @Override annotations from methods, and renaming
 * the generated implementation class to match the original mapper interface name.
 * <p>
 * The recipe performs the following key steps:
 * 1. Identifies classes annotated with Mapstruct's @Mapper annotation.
 * 2. Locates the corresponding Mapstruct-generated implementation class (e.g., `MyMapperImpl`) from the source files in context.
 * 3. Merges imports from the original interface into the implementation class.
 * 4. Removes unnecessary annotations (such as @Override) from the implementation class.
 * 5. Renames the implementation class to match the original interface name and removes
 *    "implements" declarations.
 * <p>
 * This recipe assumes that the generated implementation is available in the source files being processed.
 * The gradle plugin should be configured to include generated sources in the context.
 * <p>
 * Note: This recipe does not copy default methods from the interface. It only works with
 * interfaces that do not have default methods.
 * <p>
 * It is recommended to run supplementary cleanup tools or recipes (e.g., RemoveUnusedImports)
 * following this recipe to handle any redundant imports or formatting inconsistencies introduced during the process.
 */
@NullMarked
@Log
@Value
@EqualsAndHashCode(callSuper = false)
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
        Accumulator acc = new Accumulator();
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                // First, scan this file for generated implementation classes
                new ImplementationScanner(acc).visitCompilationUnit(cu, ctx);
                // Then, process this file for mapper interfaces
                return new MapperProcessor(acc).visitCompilationUnit(cu, ctx);
            }
        };
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class Accumulator {
        // Maps interface FQN to its generated implementation compilation unit
        Map<String, J.CompilationUnit> implClasses = new HashMap<>();
    }

    private static class ImplementationScanner extends JavaIsoVisitor<ExecutionContext> {
        private final Accumulator acc;

        ImplementationScanner(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            // Look for classes that end with "Impl" and implement an interface
            // These are likely MapStruct generated classes
            for (J.ClassDeclaration classDecl : cu.getClasses()) {
                String className = classDecl.getName().getSimpleName();
                if (className.endsWith("Impl") && classDecl.getImplements() != null && !classDecl.getImplements().isEmpty()) {
                    // Extract the interface name (remove "Impl" suffix)
                    String interfaceName = className.substring(0, className.length() - 4);
                    String packageName = cu.getPackageDeclaration() != null 
                        ? cu.getPackageDeclaration().getExpression().printTrimmed(getCursor())
                        : "";
                    String interfaceFqn = packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName;
                    
                    acc.implClasses.put(interfaceFqn, cu);
                }
            }
            return super.visitCompilationUnit(cu, ctx);
        }
    }

    private static class MapperProcessor extends JavaVisitor<ExecutionContext> {
        private final Accumulator acc;

        MapperProcessor(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J visitCompilationUnit(J.CompilationUnit originalCu, ExecutionContext ctx) {
            // Skip implementation files - they're used to replace mapper interfaces, not processed themselves
            boolean isImpl = originalCu.getClasses().stream()
                    .anyMatch(cd -> {
                        String className = cd.getName().getSimpleName();
                        return className.endsWith("Impl") && cd.getImplements() != null && !cd.getImplements().isEmpty();
                    });
            if (isImpl) {
                // Return null to delete the implementation file (it's been merged into the mapper interface)
                return null;
            }

            // 1. Identify if this is a Mapstruct Mapper interface
            boolean isMapper = originalCu.getClasses().stream()
                    .anyMatch(cd -> cd.getAllAnnotations().stream()
                            .anyMatch(a -> isMapstructMapper(a)));

            if (!isMapper) {
                return super.visitCompilationUnit(originalCu, ctx);
            }

            J.ClassDeclaration originalInterface = originalCu.getClasses().get(0);

            try {
                // 2. Find the generated implementation class from the accumulator
                String interfaceFqn = getInterfaceFqn(originalCu, originalInterface);
                J.CompilationUnit implCu = acc.implClasses.get(interfaceFqn);

                if (implCu == null) {
                    log.warning("Could not find generated implementation for " + interfaceFqn + ". Skipping.");
                    return super.visitCompilationUnit(originalCu, ctx);
                }

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
                log.severe("Error processing @Mapper class " + originalCu.getClasses().get(0).getName() + ": " + e.getMessage());
                throw new RuntimeException("Failed to migrate Mapstruct Mapper: " + originalInterface.getName().getSimpleName(), e);
            }
        }

        private String getInterfaceFqn(J.CompilationUnit originalCu, J.ClassDeclaration originalInterface) {
            String className = originalInterface.getName().getSimpleName();
            String packageName = originalCu.getPackageDeclaration() != null
                    ? originalCu.getPackageDeclaration().getExpression().printTrimmed(getCursor())
                    : "";
            return packageName.isEmpty() ? className : packageName + "." + className;
        }

        private boolean isMapstructMapper(J.Annotation a) {
            return (a.getType() != null && TypeUtils.isOfClassType(a.getType(), "org.mapstruct.Mapper"))
                    || a.getSimpleName().equals("Mapper");
        }
    }
}
