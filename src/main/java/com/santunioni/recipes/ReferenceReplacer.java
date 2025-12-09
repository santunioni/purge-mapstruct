package com.santunioni.recipes;

import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Visitor that replaces references to mapper implementations with their super class/interface.
 * For example, replaces MyMapperImpl with MyMapper in imports, variable declarations, constructor calls, etc.
 */
@Log
@NullMarked
class ReferenceReplacer extends JavaIsoVisitor<ExecutionContext> {
    private final Accumulator acc;

    ReferenceReplacer(Accumulator acc) {
        this.acc = acc;
    }

    @Override
    public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
        // Try to extract FQN from type information first, then fallback to manual extraction
        String importFqn = null;
        if (import_.getQualid().getType() != null) {
            JavaType type = import_.getQualid().getType();
            if (type instanceof JavaType.FullyQualified fullyQualified) {
                importFqn = fullyQualified.getFullyQualifiedName();
            } else {
                importFqn = type.toString();
            }
        }
        
        // Fallback to manual extraction if type info not available
        if (importFqn == null) {
            importFqn = extractFqnFromFieldAccess(import_.getQualid());
        }
        
        String superFqn = null;
        if (importFqn != null) {
            superFqn = acc.getSuperFqnFromImplFqn(importFqn);
        }

        J.Import imp = super.visitImport(import_, ctx);
        if (imp == null) {
            return null;
        }

        if (superFqn != null) {
            // Check if already replaced by comparing the final identifier
            String currentSimpleName = getFinalIdentifierName(imp.getQualid());
            String expectedSimpleName = extractSimpleName(superFqn);
            if (currentSimpleName.equals(expectedSimpleName)) {
                // Already replaced, no need to change
                return imp;
            }
            // Replace the import with the super type
            return replaceImportQualid(imp, superFqn);
        }
        return imp;
    }

    /**
     * Extracts FQN from a FieldAccess by traversing the chain.
     */
    private @Nullable String extractFqnFromFieldAccess(J.FieldAccess fieldAccess) {
        StringBuilder fqn = new StringBuilder();
        J.FieldAccess current = fieldAccess;
        List<String> parts = new ArrayList<>();

        // Traverse the chain backwards to build the FQN
        while (current != null) {
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

        // Reverse to get correct order
        Collections.reverse(parts);
        if (parts.isEmpty()) {
            return null;
        }

        return String.join(".", parts);
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variableDeclarations,
                                                            ExecutionContext ctx) {
        J.VariableDeclarations varDecl = super.visitVariableDeclarations(variableDeclarations, ctx);
        if (varDecl.getTypeExpression() == null) {
            return varDecl;
        }

        TypeTree typeExpression = varDecl.getTypeExpression();
        String typeFqn = extractFqnFromTypeTree(typeExpression);
        if (typeFqn != null) {
            String superFqn = acc.getSuperFqnFromImplFqn(typeFqn);
            if (superFqn != null) {
                TypeTree newTypeExpression = replaceTypeTree(typeExpression, superFqn);
                return varDecl.withTypeExpression(newTypeExpression);
            }
        }
        return varDecl;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass newClazz = super.visitNewClass(newClass, ctx);
        if (newClazz.getClazz() == null) {
            return newClazz;
        }

        TypeTree clazz = newClazz.getClazz();
        String clazzFqn = extractFqnFromTypeTree(clazz);
        if (clazzFqn != null) {
            String superFqn = acc.getSuperFqnFromImplFqn(clazzFqn);
            if (superFqn != null) {
                TypeTree newClazzType = replaceTypeTree(clazz, superFqn);
                return newClazz.withClazz(newClazzType);
            }
        }
        return newClazz;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration methodDecl = super.visitMethodDeclaration(method, ctx);
        if (methodDecl.getReturnTypeExpression() == null) {
            return methodDecl;
        }

        TypeTree returnType = methodDecl.getReturnTypeExpression();
        String returnTypeFqn = extractFqnFromTypeTree(returnType);
        if (returnTypeFqn != null) {
            String superFqn = acc.getSuperFqnFromImplFqn(returnTypeFqn);
            if (superFqn != null) {
                TypeTree newReturnType = replaceTypeTree(returnType, superFqn);
                return methodDecl.withReturnTypeExpression(newReturnType);
            }
        }
        return methodDecl;
    }

    @Override
    public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
        J.TypeCast cast = super.visitTypeCast(typeCast, ctx);
        if (cast.getClazz() == null || cast.getClazz().getTree() == null) {
            return cast;
        }

        TypeTree clazz = cast.getClazz().getTree();
        String clazzFqn = extractFqnFromTypeTree(clazz);
        if (clazzFqn != null) {
            String superFqn = acc.getSuperFqnFromImplFqn(clazzFqn);
            if (superFqn != null) {
                TypeTree newClazzType = replaceTypeTree(clazz, superFqn);
                return cast.withClazz(cast.getClazz().withTree(newClazzType));
            }
        }
        return cast;
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
     * Extracts the simple name from the FQN and replaces the identifier.
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
            return fieldAccess.withName(newIdentifier);
        }
        // If we can't replace it properly, try creating a new identifier
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
     * Replaces the qualid in an import statement with the super type.
     */
    private J.Import replaceImportQualid(J.Import import_, String superFqn) {
        String superSimpleName = extractSimpleName(superFqn);
        J.FieldAccess qualid = import_.getQualid();

        // For qualified imports, replace the final identifier
        J.FieldAccess current = qualid;
        while (current.getTarget() instanceof J.FieldAccess nested) {
            current = nested;
        }
        J.Identifier finalIdentifier = current.getName();
        J.Identifier newIdentifier = finalIdentifier.withSimpleName(superSimpleName);
        J.FieldAccess newQualid = replaceFinalIdentifier(qualid, newIdentifier);
        return import_.withQualid(newQualid);
    }

    /**
     * Recursively replaces the final identifier in a FieldAccess chain.
     */
    private J.FieldAccess replaceFinalIdentifier(J.FieldAccess fieldAccess, J.Identifier newIdentifier) {
        if (fieldAccess.getTarget() instanceof J.FieldAccess nested) {
            return fieldAccess.withTarget(replaceFinalIdentifier(nested, newIdentifier));
        }
        return fieldAccess.withName(newIdentifier);
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

}
