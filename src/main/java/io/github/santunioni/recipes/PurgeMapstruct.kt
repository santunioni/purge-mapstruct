package io.github.santunioni.recipes

import io.github.santunioni.recipes.removeMapstruct.Accumulator
import io.github.santunioni.recipes.removeMapstruct.MapperProcessor
import org.openrewrite.ExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.java.tree.J

/**
 * Extends [PurgeMapstructBare] with [RecommendedCleanUps], applying the cleanup
 * **only to files that [MapperProcessor] actually changes**.
 *
 * Files that [MapperProcessor] does not touch (unrelated services, DTOs, etc.) are
 * returned unchanged — keeping the diff of a purge PR as small as possible.
 *
 * For a general cleanup pass that touches all Java files, use [RecommendedCleanUps] directly.
 */
class PurgeMapstruct : PurgeMapstructBare() {
    override fun getDisplayName(): String = "Purge MapStruct — Cleaner Code"

    override fun getDescription(): String =
        "Inlines every @Mapper interface/abstract class into plain Java, then applies " +
            "a curated set of cleanup and formatting recipes — but only to the files it changes."

    override fun getVisitor(acc: Accumulator): TreeVisitor<*, ExecutionContext> {
        val mapperProcessor = super.getVisitor(acc)
        val cleanupVisitors = RecommendedCleanUps.buildCleanupVisitors()

        return object : TreeVisitor<Tree, ExecutionContext>() {
            override fun isAcceptable(
                sourceFile: SourceFile,
                ctx: ExecutionContext,
            ): Boolean = mapperProcessor.isAcceptable(sourceFile, ctx)

            override fun visit(
                tree: Tree?,
                ctx: ExecutionContext,
            ): Tree? {
                // Delegate fully to MapperProcessor:
                //   - impl files   → returns null  (deletion)
                //   - mapper files → returns merged CompilationUnit
                //   - other files  → returns same or rewritten CompilationUnit (Impl ref rewrites)
                val result = mapperProcessor.visit(tree, ctx) ?: return null

                // Unchanged — skip cleanup entirely
                if (result === tree) return result

                // MapperProcessor changed this file — apply targeted cleanup
                var cu = result as? J.CompilationUnit ?: return result
                for (visitor in cleanupVisitors) {
                    @Suppress("UNCHECKED_CAST")
                    cu = (visitor as TreeVisitor<Tree, ExecutionContext>).visit(cu, ctx) as? J.CompilationUnit ?: cu
                }
                return cu
            }
        }
    }
}
