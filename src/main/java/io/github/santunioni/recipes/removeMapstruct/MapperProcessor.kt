package io.github.santunioni.recipes.removeMapstruct

import io.github.santunioni.recipes.RecommendedCleanUps
import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.java.tree.J
import java.util.logging.Logger

class MapperProcessor(
    acc: Accumulator,
) : MapperProcessorBare(acc) {
    private val log = Logger.getLogger(MapperProcessor::class.java.name)
    private val cleanupVisitors = RecommendedCleanUps().recipeList.map { it.visitor }

    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        // Delegate fully to MapperProcessor:
        //   - impl files → returns null (deletion)
        //   - mapper files → returns merged CompilationUnit
        //   - other files → return the same or rewritten CompilationUnit (Impl ref rewrites)
        val result = super.visit(tree, ctx) ?: return null

        // Unchanged — skip cleanup entirely
        if (result === tree) return result

        // MapperProcessor changed this file — apply targeted cleanup
        var cu = result as? J.CompilationUnit ?: return result
        for (visitor in cleanupVisitors) {
            @Suppress("UNCHECKED_CAST")
            cu = (visitor as TreeVisitor<Tree, ExecutionContext>).visit(cu, ctx) as? J.CompilationUnit ?: cu
        }
        log.info("Finished migrating ${cu.sourcePath}")
        return cu
    }
}
