package com.santunioni.recipes.removeMapstruct;

import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.*;

import static com.santunioni.recipes.removeMapstruct.Functions.isMapperDeclaration;
import static com.santunioni.recipes.removeMapstruct.Functions.isMapperImplementation;

@Log
@NullMarked
public class MapperProcessor extends JavaVisitor<ExecutionContext> {
    private final Accumulator acc;

    public MapperProcessor(Accumulator acc) {
        this.acc = acc;
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
            return mapperDeclFile;
        }
    }

    @Override
    public J visitImport(J.Import imp, ExecutionContext ctx) {
        return super.visitImport(imp, ctx);
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
        return super.visitMethodInvocation(method, p);
    }

    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext p) {
        return super.visitVariableDeclarations(multiVariable, p);
    }

    @Override
    public J visitVariable(J.VariableDeclarations.NamedVariable namedVariable, ExecutionContext p) {
        return super.visitVariable(namedVariable, p);
    }

    @Override
    public J visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
        return super.visitInstanceOf(instanceOf, ctx);
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
            throw new RuntimeException("Failed to migrate Mapstruct Mapper: " + mapperDecl.getName().getSimpleName(),
                    e);
        }
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

    private J.CompilationUnit copyImports(J.CompilationUnit mapperImplementationFile,
                                          J.CompilationUnit originalCompilationUnit
    ) {
        List<J.Import> allImports = ListUtils.concatAll(
                mapperImplementationFile.getImports(), originalCompilationUnit.getImports());

        var forbiddenImports = new HashSet<>(Arrays.asList(
                "org.mapstruct.Mapper",
                "javax.annotation.processing.Generated",
                "jakarta.annotation.Generated"
        ));

        var imports = new ArrayList<J.Import>();
        for (J.Import imp : allImports) {
            String importFqn = imp.getQualid().printTrimmed(getCursor());
            if (!forbiddenImports.contains(importFqn)) {
                imports.add(imp);
            }
            forbiddenImports.add(importFqn);
        }

        return mapperImplementationFile.withImports(imports);
    }

}
