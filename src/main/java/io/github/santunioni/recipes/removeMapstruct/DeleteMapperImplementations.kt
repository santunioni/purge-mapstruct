package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J

/**
 * Deletes the MapStruct-generated implementation source files (`*MapperImpl.java`).
 *
 * Returning `null` from [visit] for a compilation unit is how OpenRewrite deletes a file. Detection
 * reuses [isMapperImplementation] (the `@Generated` marker carrying `org.mapstruct`), matching the
 * files the scan pass links, so it needs nothing from the scan pass itself.
 */
class DeleteMapperImplementations : JavaVisitor<ExecutionContext>() {
    /**
     * TreeVisitor.visit is declared `@Nullable T visit(@Nullable Tree, P)` in Java so Kotlin sees the
     * return as `J?`, making it safe to return null without any unchecked-cast gymnastics.
     */
    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        if (tree is J.CompilationUnit && isMapperImplementation(tree)) {
            return null
        }
        return super.visit(tree, ctx)
    }
}
