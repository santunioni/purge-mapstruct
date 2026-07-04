package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeUtils

/**
 * Like [org.openrewrite.java.FullyQualifyTypeReference] but qualifies every resolved
 * fully qualified type rather than a single nominated type.
 *
 * Applied to mapper implementation files before their methods are copied into the merged class,
 * so that all type references are self-contained and no longer depend on the impl's imports.
 * After the merge, [org.openrewrite.java.ShortenFullyQualifiedTypeReferences] reintroduces imports.
 */
class FullyQualifyTypesInImplementation<P : Any> : JavaVisitor<P>() {
    override fun visitCompilationUnit(
        cu: J.CompilationUnit,
        p: P,
    ): J = if (isMapperImplementation(cu)) super.visitCompilationUnit(cu, p) else cu

    /**
     * If the field access already represents the fully qualified name of its own type, skip it
     * (and its children) — nothing to do.
     */
    override fun visitFieldAccess(
        fieldAccess: J.FieldAccess,
        p: P,
    ): J {
        val fqType = TypeUtils.asFullyQualified(fieldAccess.type)
        if (fqType != null && fieldAccess.isFullyQualifiedClassReference(fqType.fullyQualifiedName)) {
            return fieldAccess
        }
        return super.visitFieldAccess(fieldAccess, p)
    }

    /**
     * Replaces simple-name type identifiers with their fully-qualified name. Field references
     * (variables, parameters) are left untouched — only type-position identifiers are changed.
     */
    override fun visitIdentifier(
        identifier: J.Identifier,
        p: P,
    ): J {
        if (identifier.fieldType != null) {
            return super.visitIdentifier(identifier, p)
        }
        val fullyQualified =
            TypeUtils.asFullyQualified(identifier.type)
                ?: return super.visitIdentifier(identifier, p)
        return identifier.withSimpleName(fullyQualified.fullyQualifiedName)
    }
}
