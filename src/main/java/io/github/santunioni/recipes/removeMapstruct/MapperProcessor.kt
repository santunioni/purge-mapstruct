package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaVisitor
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
    mapstructRefsReader: MapstructRefsReader,
) : JavaVisitor<ExecutionContext>() {
    private val log = Logger.getLogger(MapperProcessor::class.java.name)

    private val preInliningRecipesThatAlwaysRun =
        staticPreInliningRecipesThatAlwaysRun +
            listOf(
                // Rewrite *Impl references (imports, new FooMapperImpl(), FooMapperImpl.class, etc.)
                // back to the mapper type across every file, including unrelated call sites the merge
                // never touches. Needs the scan-pass linkings, so it is instance- (not static-) scoped.
                RewriteImplReferences(mapstructRefsReader),
            )
    private val preInliningRecipesConditional = staticPreInliningRecipesConditional + listOf()
    private val postInliningRecipes = staticPostInliningRecipes + listOf()

    private val mapperProcessorBare = MapperProcessorBare(mapstructRefsReader)

    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        val original = tree as? J.CompilationUnit ?: return super.visit(tree, ctx)

        // Always-run pre recipes: broad rewrites whose result we keep even when this file is not
        // inlined (e.g. Mappers.getMapper at unrelated call sites, spy stubs in test files).
        var afterAlways = original
        for (visitor in preInliningRecipesThatAlwaysRun) {
            @Suppress("UNCHECKED_CAST")
            afterAlways =
                (visitor as TreeVisitor<Tree, ExecutionContext>).visit(afterAlways, ctx) as? J.CompilationUnit
                    ?: afterAlways
        }

        // Conditional pre recipes: only meaningful as preparation for the merge. If the merge does
        // not touch this file, these changes are discarded so non-inlined files stay pristine.
        var afterConditional = afterAlways
        for (visitor in preInliningRecipesConditional) {
            @Suppress("UNCHECKED_CAST")
            afterConditional =
                (visitor as TreeVisitor<Tree, ExecutionContext>).visit(afterConditional, ctx) as? J.CompilationUnit
                    ?: afterConditional
        }

        // Delegate fully to MapperProcessorBare:
        //   - impl files → returns null (deletion)
        //   - mapper files → returns merged CompilationUnit
        //   - other files → return the same or rewritten CompilationUnit (Impl ref rewrites)
        val inlined = mapperProcessorBare.visit(afterConditional, ctx) ?: return null

        // Did the merge actually change this file? If so, keep the inlined result (which carries the
        // conditional prep). If not, drop the conditional prep and fall back to the always-run
        // result — this keeps broad rewrites while leaving non-inlined files otherwise untouched.
        val bareInlined = inlined !== afterConditional
        val changed = if (bareInlined) inlined as? J.CompilationUnit ?: return inlined else afterAlways

        // Nothing changed anywhere — return the original untouched to keep the diff minimal.
        if (changed === original) return tree

        // Apply targeted cleanup to whatever changed (an inlined mapper, or a file touched only by
        // an always-run rewrite such as a spy test).
        var pos = changed
        for (visitor in postInliningRecipes) {
            @Suppress("UNCHECKED_CAST")
            pos = (visitor as TreeVisitor<Tree, ExecutionContext>).visit(pos, ctx) as? J.CompilationUnit ?: pos
        }
        log.info("Finished migrating ${pos.sourcePath}")
        return pos
    }

    companion object {
        /**
         * Pre-inlining rewrites that are valuable on their own. Their result is kept even for files
         * the merge never touches, so they run broadly across the whole codebase.
         */
        private val staticPreInliningRecipesThatAlwaysRun: List<TreeVisitor<*, ExecutionContext>> by lazy {
            listOf<TreeVisitor<*, ExecutionContext>>(
                // Rewrite Mappers.getMapper(X.class) → new X() across every file, including call
                // sites in unrelated code that the inlining pass wouldn't otherwise touch.
                ReplaceMappersGetMapper(),
                // Rewrite when(spy.x(a)).thenReturn(v) → doReturn(v).when(spy).x(a). Spy stubs live
                // in test files that aren't inlined, so this must run in the broad pre pass.
                RewriteWhenOnSpy(),
            )
        }

        /**
         * Pre-inlining rewrites that only make sense as preparation for the merge. If the merge does
         * not change the file, these are rolled back so non-inlined files are left pristine.
         */
        private val staticPreInliningRecipesConditional: List<TreeVisitor<*, ExecutionContext>> by lazy {
            listOf<TreeVisitor<*, ExecutionContext>>(
                FullyQualifyTypesInImplementation(),
            )
        }

        /**
         * Built once per JVM — [Environment.Builder.scanRuntimeClasspath] is expensive and the visitor
         * list is stateless, so sharing it across all [MapperProcessor] instances is safe.
         */
        private val staticPostInliningRecipes: List<TreeVisitor<*, ExecutionContext>> by lazy {
            listOf(
                // Rewrite any Mappers.getMapper(X.class) that was copied in from the generated impl
                // during the merge (the pre-pass only saw the mapper declaration, not the impl body).
                ReplaceMappersGetMapper(),
            ) +
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
