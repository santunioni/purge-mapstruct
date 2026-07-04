package io.github.santunioni.recipes

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.RemoveUnusedImports
import org.openrewrite.java.format.AutoFormat
import org.openrewrite.java.spring.NoAutowiredOnConstructor
import org.openrewrite.staticanalysis.InlineVariable
import org.openrewrite.staticanalysis.LambdaBlockToExpression
import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables
import org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference
import org.openrewrite.staticanalysis.UnnecessaryParentheses

/**
 * A curated set of cleanup and formatting recipes that improve readability and idiom.
 *
 * Safe to run on any Java codebase — all recipes are no-ops when they find nothing to improve.
 * The Spring recipe ([NoAutowiredOnConstructor]) is a no-op on non-Spring projects.
 *
 * Intended to be run after [PurgeMapstruct], or independently as a general cleanup pass.
 * When you want the cleanup limited to only the files [PurgeMapstruct] changed, use
 * [PurgeMapstructCleanerCode] instead.
 *
 * Required on the rewrite classpath:
 *   io.github.santunioni:purge-mapstruct          (contains this class)
 *   org.openrewrite.recipe:rewrite-static-analysis (UnnecessaryParentheses, InlineVariable, …)
 *   org.openrewrite.recipe:rewrite-spring          (NoAutowiredOnConstructor)
 */
class RecommendedCleanUps : Recipe() {
    override fun getDisplayName(): String = "Recommended Refactors"

    override fun getDescription(): String =
        "A curated set of cleanup and formatting recipes that improve readability after MapStruct " +
            "inlining, or as a standalone pass on any Java codebase."

    override fun getRecipeList(): List<Recipe> =
        listOf(
            // Remove redundant parentheses
            UnnecessaryParentheses(),
            // Inline variables that are only ever returned/thrown on the very next line
            InlineVariable(),
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
            // Apply standard Java formatting: blank lines, whitespace padding, indentation
            AutoFormat(null),
        )

    companion object {
        /**
         * Returns the same cleanup steps as visitors, for use by recipes that need to apply
         * them selectively (e.g. only to files changed by another recipe).
         */
        internal fun buildCleanupVisitors(): List<TreeVisitor<*, ExecutionContext>> =
            listOf(
                UnnecessaryParentheses().visitor,
                InlineVariable().visitor,
                RemoveUnusedLocalVariables(null, null, false).visitor,
                RemoveUnusedImports().visitor,
                LambdaBlockToExpression().visitor,
                ReplaceLambdaWithMethodReference().visitor,
                NoAutowiredOnConstructor().visitor,
                AutoFormat(null).visitor,
            )
    }
}
