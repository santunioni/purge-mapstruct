package io.github.santunioni.recipes

import io.github.santunioni.recipes.inlineMapstruct.InlineMapstructPipeline
import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.RemoveUnusedImports
import org.openrewrite.java.format.AutoFormat
import org.openrewrite.java.spring.NoAutowiredOnConstructor
import org.openrewrite.java.tree.J
import org.openrewrite.staticanalysis.InlineVariable
import org.openrewrite.staticanalysis.LambdaBlockToExpression
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables
import org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference
import org.openrewrite.staticanalysis.UnnecessaryParentheses
import java.util.concurrent.ConcurrentHashMap

/**
 * Applies cleanup visitors only to files that [InlineMapstructPipeline] actually changes.
 *
 * Files that [InlineMapstructPipeline] does not touch (unrelated services, DTOs, etc.) are
 * returned unchanged — keeping the diff of a purge PR as small as possible.
 */
public class Cleanups : Recipe() {
    override fun getDisplayName(): String = "Cleanups"

    override fun getDescription(): String = "Run after running PurgeMapstruct."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> = Visitor()

    public companion object {
        public val CLEAN_UPS: List<TreeVisitor<*, ExecutionContext>> by lazy {
            listOf(
                staticAnalysis("ExplicitInitialization"),
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
                staticAnalysis("CodeCleanup"),
                staticAnalysis("CommonStaticAnalysis"),
            ).map { it.visitor } * 3
        }

        private val cache = ConcurrentHashMap<String, Recipe>()

        private fun staticAnalysis(name: String): Recipe =
            cache.computeIfAbsent(name) {
                Environment
                    .builder()
                    .scanRuntimeClasspath()
                    .build()
                    .activateRecipes(
                        "org.openrewrite.staticanalysis.$name",
                    )
            }
    }

    public class Visitor : JavaVisitor<ExecutionContext>() {
        override fun visit(
            tree: Tree?,
            ctx: ExecutionContext,
        ): J {
            var cleaned = tree as? J.CompilationUnit ?: throw RuntimeException("Unexpected tree type")
            for (cleanup in CLEAN_UPS) {
                cleaned = cleanup.visit(tree, ctx) as? J.CompilationUnit ?: throw RuntimeException("Unexpected tree type")
            }
            return cleaned
        }
    }
}

private operator fun <T> List<T>.times(other: Int): List<T> = (1..other).flatMap { this@times }
