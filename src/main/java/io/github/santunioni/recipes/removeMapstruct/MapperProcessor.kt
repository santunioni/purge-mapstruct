package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.config.Environment
import org.openrewrite.java.RemoveUnusedImports
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences
import org.openrewrite.java.format.AutoFormat
import org.openrewrite.java.spring.NoAutowiredOnConstructor
import org.openrewrite.java.tree.J
import org.openrewrite.staticanalysis.InlineVariable
import org.openrewrite.staticanalysis.LambdaBlockToExpression
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables
import org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference
import org.openrewrite.staticanalysis.UnnecessaryParentheses
import java.util.logging.Logger

open class MapperProcessor(
    acc: Accumulator,
) : MapperProcessorBare(acc) {
    private val log = Logger.getLogger(MapperProcessor::class.java.name)

    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        var pre = tree as? J.CompilationUnit ?: return super.visit(tree, ctx)
        for (visitor in preInliningRecipes) {
            @Suppress("UNCHECKED_CAST")
            pre = (visitor as TreeVisitor<Tree, ExecutionContext>).visit(pre, ctx) as? J.CompilationUnit ?: pre
        }

        // Delegate fully to MapperProcessorBare:
        //   - impl files → returns null (deletion)
        //   - mapper files → returns merged CompilationUnit
        //   - other files → return the same or rewritten CompilationUnit (Impl ref rewrites)
        val pos = super.visit(pre, ctx) ?: return null

        // Unchanged — skip cleanup entirely
        if (pos === tree) return super.visit(tree, ctx)

        // MapperProcessor changed this file — apply targeted cleanup
        var cu = pos as? J.CompilationUnit ?: return pos
        for (visitor in postInliningRecipes) {
            @Suppress("UNCHECKED_CAST")
            cu = (visitor as TreeVisitor<Tree, ExecutionContext>).visit(cu, ctx) as? J.CompilationUnit ?: cu
        }
        log.info("Finished migrating ${cu.sourcePath}")
        return cu
    }

    companion object {
        private val preInliningRecipes: List<TreeVisitor<*, ExecutionContext>> by lazy {
            listOf(
                FullyQualifyTypesInImplementation(),
            )
        }

        /**
         * Built once per JVM — [Environment.Builder.scanRuntimeClasspath] is expensive and the visitor
         * list is stateless, so sharing it across all [MapperProcessor] instances is safe.
         */
        private val postInliningRecipes: List<TreeVisitor<*, ExecutionContext>> by lazy {
            listOf(
                // Remove redundant parentheses (also inside CodeCleanup, but running it first
                // gives AutoFormat cleaner input)
                UnnecessaryParentheses(),
                // Remove local variables that are declared but never read
                RemoveUnusedLocalVariables(null, null, false),
                // Drop imports no longer referenced after merging
                RemoveUnusedImports(),
                // Collapse single-statement lambda blocks to expressions
                LambdaBlockToExpression(),
                // Replace "x -> foo(x)" with method references where applicable
                ReplaceLambdaWithMethodReference(),
                // Remove redundant @Autowired from single-constructor beans (no-op without Spring)
                NoAutowiredOnConstructor(),
                // Inline variables that are only ever returned/thrown on the very next line.
                // Two passes handle cascaded chains (e.g. a→b→return).
                InlineVariable(),
                InlineVariable(),
                InlineVariable(),
                // Apply standard Java formatting: blank lines, whitespace padding, indentation
                AutoFormat(null),
                // Opinionated cleanup pack — includes UnnecessaryParentheses, so no need
                // to list that again after this point
                Environment
                    .builder()
                    .scanRuntimeClasspath()
                    .build()
                    .activateRecipes("org.openrewrite.staticanalysis.CodeCleanup"),
                // CodeCleanup includes ShortenFullyQualifiedTypeReferences in its recipe list,
                // but the Singleton precondition on CodeCleanup prevents sub-recipes from firing
                // in our per-file targeted loop — so we must list it explicitly here.
                ShortenFullyQualifiedTypeReferences(),
            ).map { it.visitor }
        }
    }
}
