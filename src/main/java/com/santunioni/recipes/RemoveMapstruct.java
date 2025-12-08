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
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RemoveMapstruct is a recipe designed to refactor Mapstruct mapper interfaces.
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
 * 4. Removes unnecessary annotations (such as @Override from methods and @Generated from classes) from the
 * implementation class.
 * 5. Renames the implementation class to match the original interface name and removes
 * "implements" declarations.
 * <p>
 * This recipe assumes that the generated implementation is available in the source files being processed.
 * The gradle plugin should be configured to include generated sources in the context.
 * <p>
 * Note: This recipe copies default methods, static methods, and static fields from the interface to the
 * implementation class, removing the default modifier and preserving the static modifier.
 * <p>
 * It is recommended to run supplementary cleanup tools or recipes (e.g., RemoveUnusedImports)
 * following this recipe to handle any redundant imports or formatting inconsistencies introduced during the process.
 */
@Log
@NullMarked
public class RemoveMapstruct extends ScanningRecipe<RemoveMapstruct.Accumulator> {

    /**
     * Constructor for the RemoveMapstruct class.
     * This method initializes an instance of the RemoveMapstruct recipe.
     */
    public RemoveMapstruct() {
    }

    private static boolean isMapstructImplementation(J.CompilationUnit originalCu) {
        return originalCu.getClasses().stream()
                .anyMatch(cd -> {
                    String className = cd.getName().getSimpleName();
                    return className.endsWith("Impl")
                            && cd.getImplements() != null
                            && !cd.getImplements().isEmpty()
                            && cd.getAllAnnotations().stream().anyMatch(an ->
                            TypeUtils.isOfClassType(an.getType(), "javax.annotation.processing.Generated"));
                });
    }

    @Override
    public String getDisplayName() {
        return "Replace mapstruct interface with implementation";
    }

    @Override
    public String getDescription() {
        return "Replaces @Mapper interfaces with their generated implementation. Copies imports and removes @Override"
                + " annotations from methods and @Generated annotations from classes. Copies default methods, " +
                "static methods, and static fields from the interface.";
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
        Map<String, List<J.CompilationUnit>> implClasses = new HashMap<>();

    }

    private static class ImplementationScanner extends JavaIsoVisitor<ExecutionContext> {
        private final Accumulator acc;

        ImplementationScanner(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
            // Look for classes that end with "Impl" and implement an interface
            // These are likely MapStruct generated classes
            if (!isMapstructImplementation(compilationUnit)) {
                return compilationUnit;
            }

            for (J.ClassDeclaration classDecl : compilationUnit.getClasses()) {
                String className = classDecl.getName().getSimpleName();
                String interfaceName = className.substring(0, className.length() - 4);
                if (compilationUnit.getPackageDeclaration() == null) {
                    continue;
                }
                String packageName =
                        compilationUnit.getPackageDeclaration().getExpression().printTrimmed(getCursor());
                String interfaceFqn = packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName;

                if (!acc.implClasses.containsKey(interfaceFqn)) {
                    acc.implClasses.put(interfaceFqn, new ArrayList<>());
                }

                acc.implClasses.get(interfaceFqn).add(compilationUnit);

            }
            return super.visitCompilationUnit(compilationUnit, ctx);
        }

    }

    private static class MapperProcessor extends JavaVisitor<ExecutionContext> {
        private final Accumulator acc;

        MapperProcessor(Accumulator acc) {
            this.acc = acc;
        }


        @Override
        public J visitCompilationUnit(J.CompilationUnit originalCu, ExecutionContext ctx) {
            if (isMapstructImplementation(originalCu)) {
                // Ideally, I should return null to make openrewrite delete the file.
                // However, I still need the file to copy its implementation, and openrewrite
                // makes it unavailable after I return null
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

                // Store old and new class names for constructor renaming
                String oldClassName = mapperImplementationClass.getName().getSimpleName();
                String newClassName = originalInterface.getName().getSimpleName();

                // ==========================================================
                // STEP A: COPY IMPORTS
                // ==========================================================
                // We append original imports to the implementation imports.
                // Duplicates will be handled by a subsequent "RemoveUnusedImports" recipe run.
                // Filter out Generated imports since we're removing @Generated annotations
                List<J.Import> allImports = ListUtils.concatAll(
                        mapperImplementationFile.getImports(), originalCu.getImports());
                List<J.Import> mergedImports = ListUtils.map(allImports, imp -> {
                    if (imp.getQualid() != null) {
                        String importName = imp.getQualid().printTrimmed(getCursor());
                        if (importName.equals("javax.annotation.processing.Generated")
                                || importName.equals("jakarta.annotation.Generated")) {
                            return null;
                        }
                    }
                    return imp;
                });
                mapperImplementationFile = mapperImplementationFile.withImports(mergedImports);

                // ==========================================================
                // STEP B: PREPARE GENERATED METHODS (Remove @Override and rename constructors)
                // ==========================================================
                List<Statement> copiedClassStatements = new ArrayList<>();

                // Transform methods on Impl class
                for (Statement implStatement : mapperImplementationClass.getBody().getStatements()) {
                    if (implStatement instanceof J.MethodDeclaration implMethod) {

                        // Rename the constructor
                        boolean isConstructor = implMethod.getName().getSimpleName().equals(oldClassName);
                        if (isConstructor) {
                            implMethod =
                                    implMethod.withName(implMethod.getName().withSimpleName(newClassName));
                        }

                        // Filter out annotations that look like Override or Named
                        // When removing @Override, we need to preserve the spacing before it
                        List<J.Annotation> originalAnnotations = implMethod.getLeadingAnnotations();
                        Space prefixToPreserve = null;

                        // Find if we're removing an @Override annotation and capture its prefix
                        for (J.Annotation annotation : originalAnnotations) {
                            if (annotation.getSimpleName().equals("Override")
                                    || TypeUtils.isOfClassType(annotation.getType(), "java.lang.Override")) {
                                prefixToPreserve = annotation.getPrefix();
                                break;
                            }
                        }

                        List<J.Annotation> filteredAnnotations = ListUtils.map(originalAnnotations,
                                methodAnnotation -> {
                                    if (methodAnnotation.getSimpleName().equals("Override")
                                            || TypeUtils.isOfClassType(methodAnnotation.getType(),
                                            "java.lang.Override")) {
                                        return null;
                                    }
                                    return methodAnnotation;
                                });

                        implMethod = implMethod.withLeadingAnnotations(filteredAnnotations);

                        // If we removed annotations and captured the prefix, apply it to the method
                        if (prefixToPreserve != null && filteredAnnotations.isEmpty()) {
                            implMethod = implMethod.withPrefix(prefixToPreserve);
                        }

                        copiedClassStatements.add(implMethod);
                    } else {
                        copiedClassStatements.add(implStatement);
                    }
                }

                // Copy static and default methods from the interface
                for (Statement interfaceStatement : originalInterface.getBody().getStatements()) {
                    if (interfaceStatement instanceof J.MethodDeclaration interfaceMethod) {

                        interfaceMethod = interfaceMethod.withModifiers(ListUtils.map(interfaceMethod.getModifiers(),
                                modifier -> {
                                    if (modifier.getType() == J.Modifier.Type.Default) {
                                        return modifier.withType(J.Modifier.Type.Static);
                                    }
                                    return modifier;
                                }));

                        interfaceMethod =
                                interfaceMethod.withLeadingAnnotations(ListUtils.map(interfaceMethod.getLeadingAnnotations(),
                                        methodAnnotation -> {
                                            if (methodAnnotation.getSimpleName().equals("Named")
                                                    || TypeUtils.isOfClassType(methodAnnotation.getType(),
                                                    "org.mapstruct.Named")) {
                                                return null;
                                            }
                                            return methodAnnotation;
                                        }));

                        if (interfaceMethod.getModifiers().stream()
                                .anyMatch(mod -> mod.getType() == J.Modifier.Type.Static)) {
                            copiedClassStatements.add(interfaceMethod);
                        }
                    } else if (interfaceStatement instanceof J.VariableDeclarations interfaceField) {

                        ArrayList<J.Modifier> modifiers = new ArrayList<>();

                        final var modifiersSetManual =
                                Set.of(J.Modifier.Type.Public, J.Modifier.Type.Static, J.Modifier.Type.Final);

                        modifiers.add(new J.Modifier(UUID.randomUUID(), Space.EMPTY,
                                Markers.EMPTY, null, J.Modifier.Type.Public, Collections.emptyList()));

                        modifiers.add(new J.Modifier(UUID.randomUUID(), Space.SINGLE_SPACE,
                                Markers.EMPTY, null, J.Modifier.Type.Static, Collections.emptyList()));

                        modifiers.add(new J.Modifier(UUID.randomUUID(), Space.SINGLE_SPACE,
                                Markers.EMPTY, null, J.Modifier.Type.Final, Collections.emptyList()));

                        for (J.Modifier modifier : interfaceField.getModifiers()) {
                            if (!modifiersSetManual.contains(modifier.getType())) {
                                modifiers.add(modifier.withPrefix(Space.SINGLE_SPACE));
                            }
                        }

                        // Ensure the type expression has proper spacing after modifiers
                        interfaceField = interfaceField.withModifiers(modifiers);
                        if (interfaceField.getTypeExpression() != null) {
                            interfaceField = interfaceField.withTypeExpression(
                                    interfaceField.getTypeExpression().withPrefix(Space.SINGLE_SPACE));
                        }

                        copiedClassStatements.add(interfaceField);
                    }
                }

                // ==========================================================
                // STEP C: FINALIZE CLASS STRUCTURE
                // ==========================================================
                // Update body with combined statements
                mapperImplementationClass =
                        mapperImplementationClass.withBody(mapperImplementationClass.getBody().withStatements(copiedClassStatements));

                // Remove @Generated annotation from class
                mapperImplementationClass =
                        mapperImplementationClass.withLeadingAnnotations(
                                ListUtils.map(mapperImplementationClass.getLeadingAnnotations(), a -> {
                                    if (a.getSimpleName().equals("Generated")
                                            || TypeUtils.isOfClassType(a.getType(), "javax.annotation.processing" +
                                            ".Generated")
                                            || TypeUtils.isOfClassType(a.getType(), "jakarta.annotation.Generated")) {
                                        return null;
                                    }
                                    return a;
                                }));

                // Rename class: MyMapperImpl -> MyMapper
                mapperImplementationClass =
                        mapperImplementationClass.withName(mapperImplementationClass.getName().withSimpleName(newClassName));

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
