package com.santunioni.recipes.removeMapstruct;

import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Stream;

import static com.santunioni.recipes.removeMapstruct.Functions.isMapperDeclaration;
import static com.santunioni.recipes.removeMapstruct.Functions.isMapperImplementation;

@Log
@NullMarked
public class MapperProcessor extends JavaVisitor<ExecutionContext> {
    private static final String MAPSTRUCT_GROUP = "org.mapstruct";
    private final Accumulator acc;

    public MapperProcessor(Accumulator acc) {
        this.acc = acc;
    }

    private static J.@Nullable MethodDeclaration transformMapperDeclMethod(J.MethodDeclaration mapperDeclMethod) {
        mapperDeclMethod = mapperDeclMethod.withModifiers(ListUtils.map(mapperDeclMethod.getModifiers(),
                modifier -> {
                    if (modifier.getType() == J.Modifier.Type.Default) {
                        return modifier.withType(J.Modifier.Type.Public);
                    }
                    return modifier;
                }));

        mapperDeclMethod =
                mapperDeclMethod.withLeadingAnnotations(ListUtils.filter(
                        mapperDeclMethod.getLeadingAnnotations(),
                        MapperProcessor::excludeMapstructAnnotations)
                );

        // Remove MapStruct annotations from method parameters
        List<Statement> filteredParameters = ListUtils.map(mapperDeclMethod.getParameters(),
                param -> {
                    if (param instanceof J.VariableDeclarations varDecl) {
                        return varDecl.withLeadingAnnotations(ListUtils.filter(
                                varDecl.getLeadingAnnotations(),
                                MapperProcessor::excludeMapstructAnnotations
                        ));
                    }
                    return param;
                });

        mapperDeclMethod = mapperDeclMethod.withParameters(filteredParameters);

        // Normalize method prefix to avoid extra blank lines
        Space currentPrefix = mapperDeclMethod.getPrefix();
        if (currentPrefix != null) {
            String whitespace = currentPrefix.getWhitespace();
            // Remove multiple consecutive newlines, keep only single newlines
            String normalizedWhitespace = whitespace.replaceAll("\n\n+", "\n");
            // Ensure we don't have leading blank lines
            normalizedWhitespace = normalizedWhitespace.replaceAll("^\n+", "");
            if (!normalizedWhitespace.equals(whitespace)) {
                mapperDeclMethod = mapperDeclMethod.withPrefix(Space.format(normalizedWhitespace));
            }
        }

        return mapperDeclMethod.getBody() != null ? mapperDeclMethod : null;
    }

    private static J.VariableDeclarations transformMapperDeclInterfaceField(J.VariableDeclarations mapperDeclField) {
        ArrayList<J.Modifier> modifiers = new ArrayList<>();

        final var accessModifiers = Set.of(J.Modifier.Type.Public, J.Modifier.Type.Protected, J.Modifier.Type.Private);

        final var modifiersSetManually =
                Set.of(J.Modifier.Type.Public, J.Modifier.Type.Protected, J.Modifier.Type.Private,
                        J.Modifier.Type.Static,
                        J.Modifier.Type.Final);

        final var accessModifierInPlace =
                mapperDeclField
                        .getModifiers()
                        .stream()
                        .filter(modifier -> accessModifiers.contains(modifier.getType())).findFirst();

        if (accessModifierInPlace.isPresent()) {
            modifiers.add(accessModifierInPlace.get());
        } else {
            modifiers.add(new J.Modifier(UUID.randomUUID(), Space.EMPTY,
                    Markers.EMPTY, null, J.Modifier.Type.Public, Collections.emptyList()));
        }

        modifiers.add(new J.Modifier(UUID.randomUUID(), Space.SINGLE_SPACE,
                Markers.EMPTY, null, J.Modifier.Type.Static, Collections.emptyList()));

        modifiers.add(new J.Modifier(UUID.randomUUID(), Space.SINGLE_SPACE,
                Markers.EMPTY, null, J.Modifier.Type.Final, Collections.emptyList()));

        for (J.Modifier modifier : mapperDeclField.getModifiers()) {
            if (!modifiersSetManually.contains(modifier.getType())) {
                modifiers.add(modifier.withPrefix(Space.SINGLE_SPACE));
            }
        }

        // Ensure the type expression has proper spacing after modifiers
        mapperDeclField = mapperDeclField.withModifiers(modifiers);
        if (mapperDeclField.getTypeExpression() != null) {
            mapperDeclField = mapperDeclField.withTypeExpression(
                    mapperDeclField.getTypeExpression().withPrefix(Space.SINGLE_SPACE));
        }

        return mapperDeclField;
    }

    private static J.MethodDeclaration transformMapperImplMethod(J.MethodDeclaration implMethod, String mapperImplClassName,
                                                                 String mapperDeclClassName) {
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

        return implMethod;
    }

    private static boolean excludeGeneratedAnnotations(J.Annotation a) {
        return !(
                a.getSimpleName().equals("Generated")
                        || TypeUtils.isOfClassType(a.getType(), "javax.annotation.processing.Generated")
                        || TypeUtils.isOfClassType(a.getType(), "jakarta.annotation.Generated")
        );
    }

    private static boolean excludeMapstructAnnotations(J.Annotation a) {
        if (a.getType() == null) {
            return false;
        }

        return !a.getType().toString().startsWith(MAPSTRUCT_GROUP);
    }

    /**
     * Overrides the entire mapper declaration by a modified mix of methods and fields from declaration and
     * implementation
     */
    @Override
    public J visitCompilationUnit(J.CompilationUnit mapperDeclFile_, ExecutionContext ctx) {
        J visited = super.visitCompilationUnit(mapperDeclFile_, ctx);
        if (!(visited instanceof J.CompilationUnit mapperDeclFile)) {
            return visited;
        }

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

    /**
     * Replaces references of UserMapperImpl.class to UserMapper.class
     */
    @Override
    public J visitFieldAccess(J.FieldAccess fieldAccess_, ExecutionContext ctx) {
        J visited = super.visitFieldAccess(fieldAccess_, ctx);
        if (!(visited instanceof J.FieldAccess fieldAccess)) {
            return visited;
        }

        final var target = fieldAccess.getTarget();

        if (!(target instanceof J.Identifier targetIdentifier)) {
            return fieldAccess;
        }

        final var targetType = targetIdentifier.getType();
        if (targetType == null) {
            return fieldAccess;
        }

        final var targetFqn = targetType.toString();
        final var superFqn = acc.getSuperFqnFromImplFqn(targetFqn);

        if (superFqn == null) {
            return fieldAccess;
        }

        final var superSimpleName = extractSimpleName(superFqn);
        final var superType = JavaType.buildType(superFqn);
        final Expression superTarget = new J.Identifier(
                UUID.randomUUID(),
                targetIdentifier.getPrefix(),
                targetIdentifier.getMarkers(),
                Collections.emptyList(),
                superSimpleName,
                superType,
                targetIdentifier.getFieldType()
        );

        fieldAccess = fieldAccess.withTarget(superTarget).withType(superType);

        return fieldAccess;
    }

    /**
     * Replaces import references of UserMapperImpl to UserMapper
     */
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

    /**
     * Replaces instantiations of UserMapperImpl() to UserMapper()
     */
    @Override
    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J visited = super.visitNewClass(newClass, ctx);
        if (!(visited instanceof J.NewClass newClazz)) {
            return visited;
        }

        // Replace constructor type (new MyMapperImpl() -> new MyMapper())
        TypeTree clazz = newClazz.getClazz();
        if (clazz == null) {
            return newClazz;
        }

        TypeTree replacedClazz = replaceTypeTreeIfNeeded(clazz);
        if (replacedClazz == clazz) {
            return newClazz;
        }

        return newClazz.withClazz(replacedClazz);
    }

    /**
     * Replaces variable declarations like `UserMapperImpl userMapper` to `UserMapper userMapper`
     */
    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext p) {
        J visited = super.visitVariableDeclarations(multiVariable, p);
        if (!(visited instanceof J.VariableDeclarations varDecl)) {
            return visited;
        }

        // Replace type expression (for method parameters, field declarations, etc.)
        if (varDecl.getTypeExpression() == null) {
            return varDecl;
        }

        TypeTree typeExpression = varDecl.getTypeExpression();
        TypeTree replacedTypeExpression = replaceTypeTreeIfNeeded(typeExpression);
        if (replacedTypeExpression == typeExpression) {
            return varDecl;
        }

        return varDecl.withTypeExpression(replacedTypeExpression);
    }

    /**
     * Replaces instanceof checks like `userMapper instanceof UserMapperImpl` to `userMapper instanceof UserMapper`
     */
    @Override
    public J visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
        J visited = super.visitInstanceOf(instanceOf, ctx);
        if (!(visited instanceof J.InstanceOf instanceOf_)) {
            return visited;
        }

        // Replace the type in instanceof checks
        J clazzExpr = instanceOf_.getClazz();
        if (!(clazzExpr instanceof J.ControlParentheses clazzParentheses)) {
            return instanceOf_;
        }

        Object treeObj = clazzParentheses.getTree();
        if (!(treeObj instanceof TypeTree clazz)) {
            return instanceOf_;
        }

        TypeTree replacedClazz = replaceTypeTreeIfNeeded(clazz);
        if (replacedClazz == clazz) {
            return instanceOf_;
        }

        return instanceOf_.withClazz(clazzParentheses.withTree(replacedClazz));
    }

    /**
     * Modify the mapper declaration file with a modified mix of methods and fields from declaration and implementation
     */
    private J processMapperDeclaration(J.CompilationUnit mapperDeclFile, ExecutionContext ctx) {
        J.ClassDeclaration mapperDeclClass = mapperDeclFile.getClasses().get(0);

        try {
            J.CompilationUnit mapperImplFile = acc.getImplementer(mapperDeclClass);
            if (mapperImplFile == null) {
                return super.visitCompilationUnit(mapperDeclFile, ctx);
            }

            J.ClassDeclaration mapperImplClass = mapperImplFile.getClasses().get(0);
            String mapperImplClassName = mapperImplClass.getName().getSimpleName();
            String mapperDeclClassName = mapperDeclClass.getName().getSimpleName();

            mapperImplFile = copyImports(mapperImplFile, mapperDeclFile);

            // ==========================================================
            // STEP B: PREPARE GENERATED METHODS (Remove @Override and rename constructors)
            // ==========================================================
            List<Statement> copiedClassStatements = new ArrayList<>();

            // Transform methods on Impl class
            for (Statement implStatement : mapperImplClass.getBody().getStatements()) {
                if (implStatement instanceof J.MethodDeclaration mapperImplMethod) {
                    copiedClassStatements.add(transformMapperImplMethod(
                            mapperImplMethod,
                            mapperImplClassName,
                            mapperDeclClassName
                    ));
                } else {
                    copiedClassStatements.add(implStatement);
                }
            }

            for (Statement mapperDeclStatement : mapperDeclClass.getBody().getStatements()) {
                if (mapperDeclStatement instanceof J.MethodDeclaration mapperDeclMethod) {
                    final var mapperDeclMethodNullable = transformMapperDeclMethod(mapperDeclMethod);
                    if (mapperDeclMethodNullable != null) {
                        copiedClassStatements.add(mapperDeclMethodNullable);
                    }
                } else if (mapperDeclStatement instanceof J.VariableDeclarations mapperDeclField) {
                    if (mapperDeclClass.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                        mapperDeclField = transformMapperDeclInterfaceField(mapperDeclField);
                    }
                    copiedClassStatements.add(mapperDeclField);
                }
            }

            copiedClassStatements.sort(new StatementDefinitionOrder());

            J.ClassDeclaration clazz = mapperImplClass
                    .withBody(mapperImplClass.getBody().withStatements(copiedClassStatements))
                    .withName(mapperImplClass.getName().withSimpleName(mapperDeclClassName))
                    .withImplements(null)
                    .withLeadingAnnotations(
                            Stream.concat(
                                            mapperDeclClass
                                                    .getLeadingAnnotations()
                                                    .stream().filter(MapperProcessor::excludeMapstructAnnotations),
                                            mapperImplClass
                                                    .getLeadingAnnotations()
                                                    .stream().filter(MapperProcessor::excludeGeneratedAnnotations)
                                    )
                                    .toList()
                    )
                    .withExtends(null);

            return mapperImplFile
                    .withClasses(Collections.singletonList(clazz))
                    .withId(mapperDeclFile.getId())
                    .withSourcePath(mapperDeclFile.getSourcePath());

        } catch (Exception e) {
            log.severe("Error processing @Mapper class " + mapperDeclFile.getClasses().get(0).getName() + ": " + e.getMessage());
            throw new RuntimeException("Failed to migrate Mapstruct Mapper: " + mapperDeclClass.getName().getSimpleName(),
                    e);
        }
    }

    private J.CompilationUnit copyImports(J.CompilationUnit mapperImplementationFile,
                                          J.CompilationUnit originalCompilationUnit
    ) {
        List<J.Import> allImports = ListUtils.concatAll(
                mapperImplementationFile.getImports(), originalCompilationUnit.getImports());

        var forbiddenImports = new HashSet<>(Arrays.asList(
                "javax.annotation.processing.Generated",
                "jakarta.annotation.Generated"
        ));
        
        var imports = new ArrayList<J.Import>();
        for (J.Import imp : allImports) {
            String importFqn = imp.getQualid().printTrimmed(getCursor());
            if (!forbiddenImports.contains(importFqn) && !importFqn.startsWith(MAPSTRUCT_GROUP)) {
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
     * Replaces a TypeTree if it's a mapper implementation, otherwise returns it unchanged.
     * This method encapsulates the common logic for checking and replacing mapper impl types.
     *
     * @param typeTree the TypeTree to potentially replace
     * @return the replaced TypeTree, or the original if no replacement was needed
     */
    private TypeTree replaceTypeTreeIfNeeded(TypeTree typeTree) {
        String typeFqn = extractFqnFromTypeTree(typeTree);
        if (typeFqn == null) {
            return typeTree;
        }

        String superFqn = acc.getSuperFqnFromImplFqn(typeFqn);
        if (superFqn == null) {
            return typeTree;
        }

        // Check if already replaced - compare FQNs
        if (typeFqn.equals(superFqn)) {
            return typeTree;
        }

        // Check by simple name - if it matches and doesn't end with Impl, it's already replaced
        String currentSimpleName = getSimpleNameFromTypeTree(typeTree);
        String expectedSimpleName = extractSimpleName(superFqn);
        if (currentSimpleName.equals(expectedSimpleName) && !currentSimpleName.endsWith("Impl")) {
            return typeTree;
        }

        return replaceTypeTree(typeTree, superFqn);
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
     * The root FieldAccess (returned by getQualid()) has the class name as its name.
     * We just need to replace that name.
     */
    private J.Import replaceImportQualid(J.Import import_, String superFqn) {
        String superSimpleName = extractSimpleName(superFqn);
        J.FieldAccess qualid = import_.getQualid();

        // The root FieldAccess's name is the class name we want to replace
        J.Identifier rootName = qualid.getName();
        J.Identifier newName = rootName.withSimpleName(superSimpleName);
        J.FieldAccess newQualid = qualid.withName(newName);

        return import_.withQualid(newQualid);
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
