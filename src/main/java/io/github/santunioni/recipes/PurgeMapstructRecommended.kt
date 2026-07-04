package io.github.santunioni.recipes

import io.github.santunioni.recipes.removeMapstruct.Accumulator
import io.github.santunioni.recipes.removeMapstruct.ImplementationScanner
import io.github.santunioni.recipes.removeMapstruct.MapperProcessor
import org.openrewrite.ExecutionContext
import org.openrewrite.ScanningRecipe
import org.openrewrite.SourceFile
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.java.RemoveUnusedImports
import org.openrewrite.java.format.AutoFormat
import org.openrewrite.java.spring.NoAutowiredOnConstructor
import org.openrewrite.java.tree.J
import org.openrewrite.staticanalysis.InlineVariable
import org.openrewrite.staticanalysis.LambdaBlockToExpression
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables
import org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference
import org.openrewrite.staticanalysis.UnnecessaryParentheses

/**
 * Composes [PurgeMapstruct] with a curated set of cleanup and formatting visitors,
 * applying the cleanup **only to files that [MapperProcessor] actually changes**.
 *
 * Files that MapperProcessor does not touch (e.g. unrelated services, DTOs) are
 * returned unchanged, regardless of what the cleanup visitors would have done to
 * them — keeping the diff of a purge PR as small as possible.
 *
 * The Spring visitors ([NoAutowiredOnConstructor]) are no-ops when `@Autowired` is
 * absent, so this recipe is safe to activate on non-Spring projects.
 *
 * Required on the rewrite classpath:
 *   io.github.santunioni:purge-mapstruct          (contains this class)
 *   org.openrewrite.recipe:rewrite-static-analysis (UnnecessaryParentheses, InlineVariable, …)
 *   org.openrewrite.recipe:rewrite-spring          (NoAutowiredOnConstructor)
 */
class PurgeMapstructRecommended : ScanningRecipe<Accumulator>() {
    override fun getDisplayName(): String = "Purge MapStruct"

    override fun getDescription(): String =
        "Inlines every @Mapper interface/abstract class into plain Java and applies " +
            "a curated set of cleanup and formatting recipes only to the files it changes."

    override fun getInitialValue(ctx: ExecutionContext): Accumulator = Accumulator()

    override fun getScanner(acc: Accumulator): TreeVisitor<*, ExecutionContext> = ImplementationScanner(acc)

    override fun getVisitor(acc: Accumulator): TreeVisitor<*, ExecutionContext> {
        val mapperProcessor = MapperProcessor(acc)
        val cleanupVisitors = buildCleanupVisitors()

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

    private fun buildCleanupVisitors(): List<TreeVisitor<*, ExecutionContext>> =
        listOf(
            // Remove redundant parentheses
            UnnecessaryParentheses().visitor,
            // Inline variables that are only ever returned/thrown on the very next line
            InlineVariable().visitor,
            // Remove local variables that are declared but never read
            RemoveUnusedLocalVariables(null, null, false).visitor,
            // Drop imports no longer referenced after merging (impl used FQNs, not imports)
            RemoveUnusedImports().visitor,
            // Collapse single-statement lambda blocks to expressions
            LambdaBlockToExpression().visitor,
            // Replace "x -> foo(x)" with method references where applicable
            ReplaceLambdaWithMethodReference().visitor,
            // Remove redundant @Autowired from single-constructor beans (no-op without Spring)
            NoAutowiredOnConstructor().visitor,
            // Apply standard Java formatting: blank lines, whitespace padding, indentation
            AutoFormat(null).visitor,
        )
}
