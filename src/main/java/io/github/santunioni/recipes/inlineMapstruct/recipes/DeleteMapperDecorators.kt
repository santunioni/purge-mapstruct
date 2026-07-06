package io.github.santunioni.recipes.inlineMapstruct.recipes

import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J

/**
 * Deletes the hand-written decorator source files referenced by `@DecoratedWith`. Their behaviour is
 * folded into the merged concrete mapper by [InlineDecoratedMapper], so the standalone file is
 * removed. Returning `null` from [visit] for a compilation unit is how OpenRewrite deletes a file.
 */
internal class DeleteMapperDecorators(
    private val reader: MapstructRefsReader,
) : JavaVisitor<ExecutionContext>() {
    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        if (tree is J.CompilationUnit) {
            val decorators = reader.getDecoratorFqns()
            val isDecorator =
                tree.classes.any { it.type?.fullyQualifiedName in decorators }
            if (isDecorator) return null
        }
        return super.visit(tree, ctx)
    }
}
