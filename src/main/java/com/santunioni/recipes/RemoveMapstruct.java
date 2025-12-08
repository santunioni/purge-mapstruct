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
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
public class RemoveMapstruct extends ScanningRecipe<Accumulator> {

    /**
     * Constructor for the RemoveMapstruct class.
     * This method initializes an instance of the RemoveMapstruct recipe.
     */
    public RemoveMapstruct() {
    }

    private static boolean isMapperImplementation(J.CompilationUnit compilationUnit) {
        return compilationUnit.getClasses().stream()
                .anyMatch(cd -> {
                    String className = cd.getName().getSimpleName();
                    return className.endsWith("Impl")
                            && ((cd.getImplements() != null
                            && !cd.getImplements().isEmpty()) || cd.getExtends() != null)
                            && cd.getLeadingAnnotations().stream().anyMatch(an ->
                            TypeUtils.isOfClassType(an.getType(), "javax.annotation.processing.Generated"));
                });
    }

    private static boolean isMapperDeclaration(J.CompilationUnit originalCu) {
        return originalCu.getClasses().stream()
                .anyMatch(cd -> cd.getAllAnnotations().stream()
                        .anyMatch(a -> (a.getType() != null && TypeUtils.isOfClassType(a.getType(), "org" +
                                ".mapstruct.Mapper"))
                                || a.getSimpleName().equals("Mapper")));
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

    private static class ImplementationScanner extends JavaIsoVisitor<ExecutionContext> {
        private final Accumulator acc;

        ImplementationScanner(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit mapperImpl, ExecutionContext ctx) {
            if (!isMapperImplementation(mapperImpl)) {
                return mapperImpl;
            }

            for (J.ClassDeclaration classDecl : mapperImpl.getClasses()) {
                if (mapperImpl.getPackageDeclaration() == null) {
                    continue;
                }

                List<TypeTree> implInterfaces = Objects
                        .requireNonNullElse(classDecl.getImplements(),
                                Collections.emptyList());
                for (TypeTree interfaceDecl : implInterfaces) {
                    acc.addLinking(interfaceDecl, mapperImpl);
                }

                if (classDecl.getExtends() != null) {
                    acc.addLinking(classDecl.getExtends(), mapperImpl);
                }

            }
            return super.visitCompilationUnit(mapperImpl, ctx);
        }

    }

    private static class MapperProcessor extends JavaVisitor<ExecutionContext> {
        private final Accumulator acc;

        MapperProcessor(Accumulator acc) {
            this.acc = acc;
        }

        private static void captureMapperDeclMethod(J.MethodDeclaration mapperDeclMethod,
                                                    List<Statement> copiedClassStatements) {
            mapperDeclMethod = mapperDeclMethod.withModifiers(ListUtils.map(mapperDeclMethod.getModifiers(),
                    modifier -> {
                        if (modifier.getType() == J.Modifier.Type.Default) {
                            return modifier.withType(J.Modifier.Type.Static);
                        }
                        return modifier;
                    }));

            mapperDeclMethod =
                    mapperDeclMethod.withLeadingAnnotations(ListUtils.map(mapperDeclMethod.getLeadingAnnotations(),
                            methodAnnotation -> {
                                if (methodAnnotation.getSimpleName().equals("Named")
                                        || TypeUtils.isOfClassType(methodAnnotation.getType(),
                                        "org.mapstruct.Named")) {
                                    return null;
                                }
                                return methodAnnotation;
                            }));

            if (mapperDeclMethod.getModifiers().stream()
                    .anyMatch(mod -> mod.getType() == J.Modifier.Type.Static)) {
                copiedClassStatements.add(mapperDeclMethod);
            }
        }

        private static void captureMapperDeclField(J.VariableDeclarations mapperDeclField,
                                                   List<Statement> copiedClassStatements) {
            ArrayList<J.Modifier> modifiers = new ArrayList<>();

            final var modifiersSetManual =
                    Set.of(J.Modifier.Type.Public, J.Modifier.Type.Static, J.Modifier.Type.Final);

            modifiers.add(new J.Modifier(UUID.randomUUID(), Space.EMPTY,
                    Markers.EMPTY, null, J.Modifier.Type.Public, Collections.emptyList()));

            modifiers.add(new J.Modifier(UUID.randomUUID(), Space.SINGLE_SPACE,
                    Markers.EMPTY, null, J.Modifier.Type.Static, Collections.emptyList()));

            modifiers.add(new J.Modifier(UUID.randomUUID(), Space.SINGLE_SPACE,
                    Markers.EMPTY, null, J.Modifier.Type.Final, Collections.emptyList()));

            for (J.Modifier modifier : mapperDeclField.getModifiers()) {
                if (!modifiersSetManual.contains(modifier.getType())) {
                    modifiers.add(modifier.withPrefix(Space.SINGLE_SPACE));
                }
            }

            // Ensure the type expression has proper spacing after modifiers
            mapperDeclField = mapperDeclField.withModifiers(modifiers);
            if (mapperDeclField.getTypeExpression() != null) {
                mapperDeclField = mapperDeclField.withTypeExpression(
                        mapperDeclField.getTypeExpression().withPrefix(Space.SINGLE_SPACE));
            }

            copiedClassStatements.add(mapperDeclField);
        }

        private static J.ClassDeclaration cleanGeneratedAnnotations(J.ClassDeclaration mapperImplClass) {
            return mapperImplClass.withLeadingAnnotations(
                    ListUtils.map(mapperImplClass.getLeadingAnnotations(), a -> {
                        if (a.getSimpleName().equals("Generated")
                                || TypeUtils.isOfClassType(a.getType(), "javax.annotation.processing" +
                                ".Generated")
                                || TypeUtils.isOfClassType(a.getType(), "jakarta.annotation.Generated")) {
                            return null;
                        }
                        return a;
                    }));
        }

        private static void captureMapperImplMethod(J.MethodDeclaration implMethod, String mapperImplClassName,
                                                    String mapperDeclClassName, List<Statement> copiedClassStatements) {
            // Rename the constructor
            boolean isConstructor =
                    implMethod.getName().getSimpleName().equals(mapperImplClassName);
            if (isConstructor) {
                implMethod =
                        implMethod.withName(implMethod.getName().withSimpleName(mapperDeclClassName));
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
        }

        @Override
        public J visitCompilationUnit(J.CompilationUnit mapperDeclFile, ExecutionContext ctx) {
            if (isMapperDeclaration(mapperDeclFile)) {
                return processMapperDeclaration(mapperDeclFile, ctx);
            } else if (isMapperImplementation(mapperDeclFile)) {
                // Ideally, I should return null to make openrewrite delete the file.
                // However, I still need the file to copy its implementation, and openrewrite
                // makes it unavailable after I return null
                return mapperDeclFile;
            } else {
                return super.visitCompilationUnit(mapperDeclFile, ctx);
            }
        }

        private J processMapperDeclaration(J.CompilationUnit mapperDeclFile, ExecutionContext ctx) {
            J.ClassDeclaration mapperDecl = mapperDeclFile.getClasses().get(0);

            try {
                J.CompilationUnit mapperImplFile = acc.getImplementer(mapperDecl);
                if (mapperImplFile == null) {
                    return super.visitCompilationUnit(mapperDeclFile, ctx);
                }

                J.ClassDeclaration mapperImplClass = mapperImplFile.getClasses().get(0);
                String mapperImplClassName = mapperImplClass.getName().getSimpleName();
                String mapperDeclClassName = mapperDecl.getName().getSimpleName();

                mapperImplFile = copyImports(mapperImplFile, mapperDeclFile);

                // ==========================================================
                // STEP B: PREPARE GENERATED METHODS (Remove @Override and rename constructors)
                // ==========================================================
                List<Statement> copiedClassStatements = new ArrayList<>();

                // Transform methods on Impl class
                for (Statement implStatement : mapperImplClass.getBody().getStatements()) {
                    if (implStatement instanceof J.MethodDeclaration mapperImplMethod) {
                        captureMapperImplMethod(
                                mapperImplMethod,
                                mapperImplClassName,
                                mapperDeclClassName,
                                copiedClassStatements
                        );
                    } else {
                        copiedClassStatements.add(implStatement);
                    }
                }

                for (Statement mapperDeclStatement : mapperDecl.getBody().getStatements()) {
                    if (mapperDeclStatement instanceof J.MethodDeclaration mapperDeclMethod) {
                        captureMapperDeclMethod(mapperDeclMethod, copiedClassStatements);
                    } else if (mapperDeclStatement instanceof J.VariableDeclarations mapperDeclField) {
                        captureMapperDeclField(mapperDeclField, copiedClassStatements);
                    }
                }

                List<J.ClassDeclaration> classes = Collections.singletonList(cleanGeneratedAnnotations(
                        mapperImplClass
                                .withBody(mapperImplClass.getBody().withStatements(copiedClassStatements))
                                .withName(mapperImplClass.getName().withSimpleName(mapperDeclClassName))
                                .withImplements(null)
                                .withExtends(null)
                ));

                return mapperImplFile
                        .withClasses(classes)
                        .withId(mapperDeclFile.getId())
                        .withSourcePath(mapperDeclFile.getSourcePath());

            } catch (Exception e) {
                log.severe("Error processing @Mapper class " + mapperDeclFile.getClasses().get(0).getName() + ": " + e.getMessage());
                throw new RuntimeException("Failed to migrate Mapstruct Mapper: " + mapperDecl.getName().getSimpleName(), e);
            }
        }

        /**
         * STEP A: COPY IMPORTS
         * <p>
         * We append original imports to the implementation imports.
         * Duplicates will be handled by a subsequent "RemoveUnusedImports" recipe run.
         */
        private J.CompilationUnit copyImports(J.CompilationUnit mapperImplementationFile,
                                              J.CompilationUnit originalCompilationUnit
        ) {
            List<J.Import> allImports = ListUtils.concatAll(
                    mapperImplementationFile.getImports(), originalCompilationUnit.getImports());
            List<J.Import> mergedImports = ListUtils.map(allImports, imp -> {
                String importName = imp.getQualid().printTrimmed(getCursor());
                if (importName.equals("javax.annotation.processing.Generated")
                        || importName.equals("jakarta.annotation.Generated")) {
                    return null;
                }
                return imp;
            });
            mapperImplementationFile = mapperImplementationFile.withImports(mergedImports);
            return mapperImplementationFile;
        }


    }

}
