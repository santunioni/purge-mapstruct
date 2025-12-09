package com.santunioni.recipes.removeMapstruct;

import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
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
            return super.visitCompilationUnit(mapperDeclFile, ctx);
        }
    }

    @Override
    public J visitImport(J.Import imp, ExecutionContext ctx) {
        J visited = super.visitImport(imp, ctx);
        if (!(visited instanceof J.Import import_)) {
            return visited;
        }

        // Extract FQN from import qualid
        String importFqn = extractFqnFromFieldAccess(import_.getQualid());
        if (importFqn == null) {
            return import_;
        }

        // Check if this is a mapper implementation that needs replacement
        String superFqn = acc.getSuperFqnFromImplFqn(importFqn);
        if (superFqn == null) {
            // Not a mapper impl - check if it's already a super type (to avoid replacing backwards)
            // If this FQN is a super type for some impl, don't touch it
            return import_;
        }

        // Check if already replaced - the import FQN should match super FQN
        if (importFqn.equals(superFqn)) {
            return import_;
        }

        // Check by simple name - if it matches and doesn't end with Impl, it's already replaced
        String currentSimpleName = getFinalIdentifierName(import_.getQualid());
        String expectedSimpleName = extractSimpleName(superFqn);
        if (currentSimpleName.equals(expectedSimpleName) && !currentSimpleName.endsWith("Impl")) {
            return import_;
        }

        // Replace the import with the super type
        return replaceImportQualid(import_, superFqn);
    }

    @Override
    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J visited = super.visitNewClass(newClass, ctx);
        if (!(visited instanceof J.NewClass newClazz)) {
            return visited;
        }

        // Replace constructor type (new MyMapperImpl() -> new MyMapper())
        TypeTree clazz = newClazz.getClazz();
        if (clazz != null) {
            TypeTree replacedClazz = replaceTypeTreeIfNeeded(clazz);
            if (replacedClazz != clazz) {
                return newClazz.withClazz(replacedClazz);
            }
        }

        return newClazz;
    }

    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext p) {
        J visited = super.visitVariableDeclarations(multiVariable, p);
        if (!(visited instanceof J.VariableDeclarations varDecl)) {
            return visited;
        }

        // Replace type expression (for method parameters, field declarations, etc.)
        if (varDecl.getTypeExpression() != null) {
            TypeTree typeExpression = varDecl.getTypeExpression();
            TypeTree replacedTypeExpression = replaceTypeTreeIfNeeded(typeExpression);
            if (replacedTypeExpression != typeExpression) {
                return varDecl.withTypeExpression(replacedTypeExpression);
            }
        }

        return varDecl;
    }

    @Override
    public J visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
        J visited = super.visitInstanceOf(instanceOf, ctx);
        if (!(visited instanceof J.InstanceOf instanceOf_)) {
            return visited;
        }

        // Replace the type in instanceof checks
        J clazzExpr = instanceOf_.getClazz();
        if (clazzExpr instanceof J.ControlParentheses clazzParentheses) {
            Object treeObj = clazzParentheses.getTree();
            if (treeObj instanceof TypeTree clazz) {
                TypeTree replacedClazz = replaceTypeTreeIfNeeded(clazz);
                if (replacedClazz != clazz) {
                    return instanceOf_.withClazz(clazzParentheses.withTree(replacedClazz));
                }
            }
        }

        return instanceOf_;
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

    /**
     * Extracts FQN from a FieldAccess (used for imports).
     * Prioritizes name-based extraction to avoid stale type information after replacements.
     */
    private @Nullable String extractFqnFromFieldAccess(J.FieldAccess fieldAccess) {
        // First try to extract from the name chain (more reliable after replacements)
        List<String> parts = new ArrayList<>();
        J.FieldAccess current = fieldAccess;
        while (true) {
            parts.add(current.getName().getSimpleName());
            if (current.getTarget() instanceof J.FieldAccess nested) {
                current = nested;
            } else if (current.getTarget() instanceof J.Identifier identifier) {
                parts.add(identifier.getSimpleName());
                break;
            } else {
                break;
            }
        }
        Collections.reverse(parts);
        String nameBasedFqn = parts.isEmpty() ? null : String.join(".", parts);

        // If we got a valid FQN from names, use it (more reliable after replacements)
        if (nameBasedFqn != null && !nameBasedFqn.isEmpty()) {
            return nameBasedFqn;
        }

        // Fallback to type information if name extraction failed
        if (fieldAccess.getType() != null) {
            JavaType type = fieldAccess.getType();
            if (type instanceof JavaType.FullyQualified fullyQualified) {
                return fullyQualified.getFullyQualifiedName();
            }
            return type.toString();
        }

        return null;
    }

    /**
     * Extracts FQN from a TypeTree, following the pattern used in Accumulator.addLinking.
     */
    private @Nullable String extractFqnFromTypeTree(TypeTree typeTree) {
        if (typeTree.getType() == null) {
            return null;
        }
        JavaType type = typeTree.getType();
        if (type instanceof JavaType.FullyQualified fullyQualified) {
            return fullyQualified.getFullyQualifiedName();
        }
        // Fallback to toString() as used in Accumulator.addLinking line 24
        return type.toString();
    }

    /**
     * Replaces a TypeTree with a new one using the super FQN.
     */
    private TypeTree replaceTypeTree(TypeTree typeTree, String superFqn) {
        String superSimpleName = extractSimpleName(superFqn);

        if (typeTree instanceof J.Identifier identifier) {
            return identifier.withSimpleName(superSimpleName);
        } else if (typeTree instanceof J.FieldAccess fieldAccess) {
            // Replace the final identifier in the field access chain
            J.FieldAccess current = fieldAccess;
            while (current.getTarget() instanceof J.FieldAccess nested) {
                current = nested;
            }
            J.Identifier finalIdentifier = current.getName();
            J.Identifier newIdentifier = finalIdentifier.withSimpleName(superSimpleName);
            return replaceFinalIdentifierInChain(fieldAccess, newIdentifier);
        }
        // Fallback: create a new identifier
        return new J.Identifier(
                UUID.randomUUID(),
                typeTree.getPrefix(),
                typeTree.getMarkers(),
                Collections.emptyList(),
                superSimpleName,
                null,
                null
        );
    }

    /**
     * Recursively replaces the final identifier in a FieldAccess chain.
     */
    private J.FieldAccess replaceFinalIdentifierInChain(J.FieldAccess fieldAccess, J.Identifier newIdentifier) {
        if (fieldAccess.getTarget() instanceof J.FieldAccess nested) {
            // Continue down the chain
            J.FieldAccess newTarget = replaceFinalIdentifierInChain(nested, newIdentifier);
            return fieldAccess.withTarget(newTarget);
        }
        // This is the final FieldAccess, replace its name
        return fieldAccess.withName(newIdentifier);
    }

    /**
     * Replaces the qualid in an import statement with the super type.
     * Uses replaceFinalIdentifierInChain to maintain consistency.
     */
    private J.Import replaceImportQualid(J.Import import_, String superFqn) {
        String superSimpleName = extractSimpleName(superFqn);
        J.FieldAccess qualid = import_.getQualid();

        // Find the final identifier and replace it
        J.FieldAccess current = qualid;
        while (current.getTarget() instanceof J.FieldAccess nested) {
            current = nested;
        }
        J.Identifier finalIdentifier = current.getName();
        J.Identifier newIdentifier = finalIdentifier.withSimpleName(superSimpleName);
        J.FieldAccess newQualid = replaceFinalIdentifierInChain(qualid, newIdentifier);

        return import_.withQualid(newQualid);
    }

    /**
     * Checks if a TypeTree needs replacement and replaces it if needed.
     * Returns the original TypeTree if no replacement is needed, or a new TypeTree if replaced.
     * This method consolidates the common pattern of checking and replacing mapper implementation types.
     */
    private TypeTree replaceTypeTreeIfNeeded(TypeTree typeTree) {
        if (typeTree == null) {
            return null;
        }

        String typeFqn = extractFqnFromTypeTree(typeTree);
        if (typeFqn == null) {
            return typeTree;
        }

        String superFqn = acc.getSuperFqnFromImplFqn(typeFqn);
        if (superFqn == null) {
            return typeTree;
        }

        // Check if already replaced
        if (typeFqn.equals(superFqn)) {
            return typeTree;
        }

        String currentSimpleName = getSimpleNameFromTypeTree(typeTree);
        String expectedSimpleName = extractSimpleName(superFqn);
        if (currentSimpleName.equals(expectedSimpleName) && !currentSimpleName.endsWith("Impl")) {
            return typeTree;
        }

        return replaceTypeTree(typeTree, superFqn);
    }

    /**
     * Extracts the simple name (last part) from an FQN.
     */
    private String extractSimpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    /**
     * Gets the final identifier name from a FieldAccess chain.
     */
    private String getFinalIdentifierName(J.FieldAccess fieldAccess) {
        J.FieldAccess current = fieldAccess;
        while (current.getTarget() instanceof J.FieldAccess nested) {
            current = nested;
        }
        return current.getName().getSimpleName();
    }

    /**
     * Gets the simple name from a TypeTree.
     */
    private String getSimpleNameFromTypeTree(TypeTree typeTree) {
        if (typeTree instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        } else if (typeTree instanceof J.FieldAccess fieldAccess) {
            return getFinalIdentifierName(fieldAccess);
        }
        return "";
    }

}
