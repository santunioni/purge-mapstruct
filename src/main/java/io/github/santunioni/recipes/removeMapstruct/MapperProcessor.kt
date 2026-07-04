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
    private val cleanupVisitors: List<TreeVisitor<*, ExecutionContext>> =
        listOf(
            // Remove redundant parentheses
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
            // Inline variables that are only ever returned/thrown on the very next line
            InlineVariable(),
            InlineVariable(),
            InlineVariable(),
            // Apply standard Java formatting: blank lines, whitespace padding, indentation
            AutoFormat(null),
            // Opinionated cleanup pack
            Environment
                .builder()
                .scanRuntimeClasspath()
                .build()
                .activateRecipes("org.openrewrite.staticanalysis.CodeCleanup"),
            // Remove the FQN types
            ShortenFullyQualifiedTypeReferences(),
        ).map { it.visitor }

    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        // Delegate fully to MapperProcessorBare:
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
