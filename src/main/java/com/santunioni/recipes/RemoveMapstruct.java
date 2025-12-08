package com.santunioni.recipes;


import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
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
 * 2. Locates the corresponding Mapstruct-generated implementation class (e.g., `MyMapperImpl`) from the source files
 * in context.
 * 3. Merges imports from the original interface into the implementation class.
 * 4. Removes unnecessary annotations (such as @Override) from the implementation class.
 * 5. Renames the implementation class to match the original interface name and removes
 * "implements" declarations.
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
@Log
@NullMarked
public class RemoveMapstruct extends ScanningRecipe<RemoveMapstruct.Accumulator> {

    /**
     * Constructor for the ReplaceMapstructWithImpl class.
     * This method initializes an instance of the ReplaceMapstructWithImpl recipe.
     */
    public RemoveMapstruct() {
    }

    @Override
    public String getDisplayName() {
        return "Replace mapstruct interface with implementation";
    }

    @Override
    public String getDescription() {
        return "Replaces @Mapper interfaces with their generated implementation. Copies imports and removes @Override"
                + " annotations. Only supports interfaces without default methods.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new ImplementationScanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new MapperProcessor(acc);
    }

    @NullMarked
    public static class Accumulator {
        // Maps interface FQN to its generated implementation compilation unit
        Map<String, List<J.CompilationUnit>> implClasses = new HashMap<>();

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
                if (className.endsWith("Impl")
                        && classDecl.getImplements() != null
                        && !classDecl.getImplements().isEmpty()) {
                    // Extract the interface name (remove "Impl" suffix)
                    String interfaceName = className.substring(0, className.length() - 4);
                    if (cu.getPackageDeclaration() == null) {
                        continue;
                    }
                    String packageName = cu.getPackageDeclaration().getExpression().printTrimmed(getCursor());
                    String interfaceFqn = packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName;

                    if (!acc.implClasses.containsKey(interfaceFqn)) {
                        acc.implClasses.put(interfaceFqn, new ArrayList<>());
                    }

                    acc.implClasses.get(interfaceFqn).add(cu);
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
                        return className.endsWith("Impl")
                                && cd.getImplements() != null
                                && !cd.getImplements().isEmpty()
                                && cd.getAllAnnotations().stream().anyMatch(an ->
                                TypeUtils.isOfClassType(an.getType(), "javax.annotation.processing.Generated"));
                    });
            if (isImpl) {
                // Return null to delete the implementation file (it's been merged into the mapper interface)
                return originalCu;
            }

            // 1. Identify if this is a Mapstruct Mapper interface
            boolean isMapper = originalCu.getClasses().stream()
                    .anyMatch(cd -> cd.getAllAnnotations().stream()
                            .anyMatch(this::isMapstructMapper));

            if (!isMapper) {
                return super.visitCompilationUnit(originalCu, ctx);
            }

            J.ClassDeclaration originalInterface = originalCu.getClasses().get(0);

            try {
                // 2. Find the generated implementation class from the accumulator
                String interfaceFqn = getInterfaceFqn(originalCu, originalInterface);
                List<J.CompilationUnit> implClasses = acc.implClasses.get(interfaceFqn);
                if (implClasses == null || implClasses.size() != 1) {
                    log.severe("Multiple or no generated implementations found for " + interfaceFqn + ". Skipping.");
                    return super.visitCompilationUnit(originalCu, ctx);
                }

                J.CompilationUnit mapperImplementationFile = implClasses.get(0);
                J.ClassDeclaration mapperImplementationClass = mapperImplementationFile.getClasses().get(0);

                // ==========================================================
                // STEP A: COPY IMPORTS
                // ==========================================================
                // We append original imports to the implementation imports.
                // Duplicates will be handled by a subsequent "RemoveUnusedImports" recipe run.
                List<J.Import> mergedImports = ListUtils.concatAll(
                        mapperImplementationFile.getImports(), originalCu.getImports());
                mapperImplementationFile = mapperImplementationFile.withImports(mergedImports);

                // ==========================================================
                // STEP B: PREPARE GENERATED METHODS (Remove @Override)
                // ==========================================================
                List<Statement> classStatements = new ArrayList<>();

                for (Statement s : mapperImplementationClass.getBody().getStatements()) {
                    if (s instanceof J.MethodDeclaration) {
                        J.MethodDeclaration m = (J.MethodDeclaration) s;
                        // Filter out annotations that look like Override
                        List<J.Annotation> cleanedAnnotations = ListUtils.map(m.getLeadingAnnotations(), a -> {
                            if (a.getSimpleName().equals("Override")
                                    || TypeUtils.isOfClassType(a.getType(), "java.lang.Override")) {
                                return null;
                            }
                            return a;
                        });
                        classStatements.add(m.withLeadingAnnotations(cleanedAnnotations));
                    } else {
                        classStatements.add(s);
                    }
                }

                // Copy default methods from the interface
                for (Statement s : originalInterface.getBody().getStatements()) {
                    if (s instanceof J.MethodDeclaration) {
                        J.MethodDeclaration method = (J.MethodDeclaration) s;
                        if (method.getModifiers().stream()
                                .anyMatch(mod -> mod.getType() == J.Modifier.Type.Default)) {
                            // Remove default modifier
                            List<J.Modifier> modifiers = ListUtils.map(method.getModifiers(), mod -> {
                                if (mod.getType() == J.Modifier.Type.Default) {
                                    return null;
                                }
                                return mod;
                            });
                            // Add public if missing (use the first method's public modifier as a template)
                            boolean hasPublic = modifiers.stream()
                                    .anyMatch(mod -> mod.getType() == J.Modifier.Type.Public);
                            if (!hasPublic && !classStatements.isEmpty() && classStatements.get(0) instanceof J.MethodDeclaration) {
                                J.Modifier publicMod =
                                        ((J.MethodDeclaration) classStatements.get(0)).getModifiers().stream()
                                                .filter(mod -> mod.getType() == J.Modifier.Type.Public)
                                                .findFirst()
                                                .orElse(null);
                                if (publicMod != null) {
                                    List<J.Modifier> modifiersWithPublic = new ArrayList<>();
                                    modifiersWithPublic.add(publicMod);
                                    modifiersWithPublic.addAll(modifiers);
                                    modifiers = modifiersWithPublic;
                                }
                            }
                            classStatements.add(method.withModifiers(modifiers));
                        }
                    }
                }

                // ==========================================================
                // STEP C: FINALIZE CLASS STRUCTURE
                // ==========================================================
                // Update body with combined statements
                mapperImplementationClass =
                        mapperImplementationClass.withBody(mapperImplementationClass.getBody().withStatements(classStatements));

                // Rename class: MyMapperImpl -> MyMapper
                mapperImplementationClass =
                        mapperImplementationClass.withName(mapperImplementationClass.getName().withSimpleName(originalInterface.getName().getSimpleName()));

                // Remove "implements MyMapper"
                mapperImplementationClass = mapperImplementationClass.withImplements(null);

                // Replace the class in the CU
                mapperImplementationFile =
                        mapperImplementationFile.withClasses(Collections.singletonList(mapperImplementationClass));

                // Return the new CU, masquerading as the old file (preserving ID and Path)
                return mapperImplementationFile
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
