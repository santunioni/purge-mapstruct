package io.github.santunioni.recipes.inlineMapstruct

import io.github.santunioni.recipes.inlineMapstruct.recipes.DeleteMapperDecorators
import io.github.santunioni.recipes.inlineMapstruct.recipes.DeleteMapperImplementations
import io.github.santunioni.recipes.inlineMapstruct.recipes.FullyQualifyTypesInImplementation
import io.github.santunioni.recipes.inlineMapstruct.recipes.InlineDecoratedMapper
import io.github.santunioni.recipes.inlineMapstruct.recipes.InlineMapstruct
import io.github.santunioni.recipes.inlineMapstruct.recipes.MapstructRefsReader
import io.github.santunioni.recipes.inlineMapstruct.recipes.ReplaceMappersGetMapper
import io.github.santunioni.recipes.inlineMapstruct.recipes.RewriteImplReferences
import io.github.santunioni.recipes.inlineMapstruct.recipes.RewriteWhenOnSpy
import io.github.santunioni.recipes.inlineMapstruct.recipes.StripMapstructAnnotations
import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.config.Environment.builder
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

internal class InlineMapstructPipeline(
    mapstructRefsReader: MapstructRefsReader,
) : JavaVisitor<ExecutionContext>() {
    private val log = Logger.getLogger(InlineMapstructPipeline::class.java.name)

    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        val original = tree as? J.CompilationUnit ?: return super.visit(tree, ctx)

        // Always-run pre recipes: broad rewrites whose result we keep even when this file is not
        // inlined (e.g. Mappers.getMapper at unrelated call sites, spy stubs in test files).
        var afterAlways = original
        for (visitor in preApplyToAll) {
            @Suppress("UNCHECKED_CAST")
            afterAlways =
                (visitor as TreeVisitor<Tree, ExecutionContext>).visit(afterAlways, ctx) as? J.CompilationUnit
                    ?: return null
        }

        // Conditional pre recipes: only meaningful as preparation for the merge. If the merge does
        // not touch this file, these changes are discarded so non-inlined files stay pristine.
        var afterConditional = afterAlways
        for (visitor in preApplyToTouchedFiles) {
            @Suppress("UNCHECKED_CAST")
            afterConditional =
                (visitor as TreeVisitor<Tree, ExecutionContext>).visit(afterConditional, ctx) as? J.CompilationUnit
                    ?: return null
        }

        // Delegate fully to MapperProcessorBare:
        //   - impl files → returns null (deletion)
        //   - mapper files → returns merged CompilationUnit
        //   - other files → return the same or rewritten CompilationUnit (Impl ref rewrites)
        val inlinedPlain = inlineMapstruct.visit(afterConditional, ctx) ?: return null
        // Decorated mappers (@DecoratedWith) are handled by a dedicated merge; InlineMapstruct leaves
        // them untouched, so chaining is safe — the two never act on the same file.
        val inlined = inlineDecoratedMapper.visit(inlinedPlain, ctx) ?: return null

        // Did the merge actually change this file? If so, keep the inlined result (which carries the
        // conditional prep). If not, drop the conditional prep and fall back to the always-run
        // result — this keeps broad rewrites while leaving non-inlined files otherwise untouched.
        val changed = if (inlined !== afterConditional) inlined as? J.CompilationUnit ?: return inlined else afterAlways
        if (changed === original) return afterAlways

        // Apply targeted cleanup to whatever changed (an inlined mapper, or a file touched only by
        // an always-run rewrite such as a spy test).
        var pos = changed
        for (visitor in postApplyToTouchedFiles) {
            @Suppress("UNCHECKED_CAST")
            pos = (visitor as TreeVisitor<Tree, ExecutionContext>).visit(pos, ctx) as? J.CompilationUnit ?: return null
        }
        log.info("Finished migrating ${pos.sourcePath}")
        return pos
    }

    private val preApplyToAll =
        listOf<TreeVisitor<*, ExecutionContext>>(
            // Rewrite Mappers.getMapper(X.class) → new X() across every file, including call
            // sites in unrelated code that the inlining pass wouldn't otherwise touch.
            ReplaceMappersGetMapper(),
            // Rewrite when(spy.x(a)).thenReturn(v) → doReturn(v).when(spy).x(a). Spy stubs live
            // in test files that aren't inlined, so this must run in the broad pre-pass.
            RewriteWhenOnSpy(),
            // We can delete the files generated by mapstruct because the implementation will get copied into code.
            DeleteMapperImplementations(),
            // Delete hand-written @DecoratedWith decorators — their logic is folded into the merged class.
            DeleteMapperDecorators(mapstructRefsReader),
        ) +
            listOf(
                // Rewrite *Impl references (imports, new FooMapperImpl(), FooMapperImpl.class, etc.)
                // back to the mapper type across every file, including unrelated call sites the merge
                // never touches. Needs the scan-pass linkings, so it is instance- (not static-) scoped.
                RewriteImplReferences(mapstructRefsReader),
            )

    private val preApplyToTouchedFiles =
        listOf<TreeVisitor<*, ExecutionContext>>(
            FullyQualifyTypesInImplementation(),
        )

    private val inlineMapstruct = InlineMapstruct(mapstructRefsReader)

    private val inlineDecoratedMapper = InlineDecoratedMapper(mapstructRefsReader)

    private val postApplyToTouchedFiles by lazy {
        listOf<TreeVisitor<*, ExecutionContext>>(
            // Rewrite *Impl references (new FooMapperImpl(), FooMapperImpl.class, etc.) copied in from
            // the generated impl body during the merge — same rationale as the ReplaceMappersGetMapper
            // entry at the front of the static post list. Needs the scan-pass linkings, so it is
            // instance- (not static-) scoped.
            RewriteImplReferences(mapstructRefsReader),
            // Drop MapStruct annotations (@Mapper on the type, @Mapping/@MappingTarget on methods and
            // parameters) so the inlined class is plain Java.
            StripMapstructAnnotations(),
        ) + (
            listOf(
                // Rewrite any Mappers.getMapper(X.class) that was copied in from the generated impl
                // during the merge (the pre-pass only saw the mapper declaration, not the impl body).
                ReplaceMappersGetMapper(),
            ) +
                listOf(
                    // CodeCleanup includes ShortenFullyQualifiedTypeReferences in its recipe list,
                    // but the Singleton precondition on CodeCleanup prevents sub-recipes from firing
                    // in our per-file targeted loop — so we must list it explicitly here.
                    ShortenFullyQualifiedTypeReferences(),
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
                    codeCleanup,
                ).map { it.visitor }
        )
    }

    internal companion object {
        /**
         * kept here because this is heavy to load
         */
        private val codeCleanup by lazy {
            builder()
                .scanRuntimeClasspath()
                .build()
                .activateRecipes(
                    "org.openrewrite.staticanalysis.CodeCleanup",
                    "org.openrewrite.staticanalysis.CommonStaticAnalysis",
                )
        }
    }
}
