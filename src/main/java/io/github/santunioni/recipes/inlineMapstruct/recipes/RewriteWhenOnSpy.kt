package io.github.santunioni.recipes.inlineMapstruct.recipes

import org.openrewrite.ExecutionContext
import org.openrewrite.java.AddImport
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.Expression
import org.openrewrite.java.tree.Flag
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JContainer
import org.openrewrite.java.tree.JRightPadded
import org.openrewrite.java.tree.JavaType
import org.openrewrite.java.tree.Space
import org.openrewrite.java.tree.TypeUtils
import org.openrewrite.marker.Markers
import java.util.UUID
import java.util.logging.Logger

/**
 * Rewrites Mockito stubbing on `@Spy` fields from `when(spy.x(a)).thenReturn(v)` into
 * `doReturn(v).when(spy).x(a)` (and likewise `thenThrow` → `doThrow`).
 *
 * `when(...)` invokes the real method on a spy, which is often unsafe once a mapper is inlined;
 * the `doReturn(...).when(...)` form avoids the real call. This transformation is independent of
 * MapStruct inlining and needs nothing from the scan pass — it only inspects locally declared
 * `@Spy` fields, which it collects itself per compilation unit.
 */
class RewriteWhenOnSpy : JavaVisitor<ExecutionContext>() {
    override fun visitCompilationUnit(
        cu: J.CompilationUnit,
        ctx: ExecutionContext,
    ): J {
        val spyFieldNames = collectSpyFieldNames(cu)
        if (spyFieldNames.isEmpty()) return cu
        log.fine("[PurgeMapstruct] Found spy fields in ${cu.sourcePath}: $spyFieldNames")
        cursor.putMessage(SPY_FIELD_NAMES_KEY, spyFieldNames)
        return super.visitCompilationUnit(cu, ctx)
    }

    override fun visitMethodInvocation(
        method: J.MethodInvocation,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitMethodInvocation(method, ctx)
        val visited = superResult as? J.MethodInvocation ?: return superResult
        return rewriteWhenOnSpyIfApplicable(visited) ?: visited
    }

    /**
     * If `invocation` matches `when(spy.method(args)).thenReturn(value)` where `spy` is a locally
     * declared `@Spy` field, rebuilds the tree as `doReturn(value).when(spy).method(args)` and adds
     * the necessary Mockito static import. Returns `null` when the pattern doesn't match.
     */
    private fun rewriteWhenOnSpyIfApplicable(invocation: J.MethodInvocation): J.MethodInvocation? {
        val stubName = invocation.simpleName
        if (stubName != "thenReturn" && stubName != "thenThrow") return null
        if (invocation.arguments.size != 1) return null

        val whenInvocation = invocation.select as? J.MethodInvocation ?: return null
        if (whenInvocation.simpleName != "when" || whenInvocation.arguments.size != 1) return null

        val spyMethodCall = whenInvocation.arguments[0] as? J.MethodInvocation ?: return null
        val spyIdent = spyMethodCall.select as? J.Identifier ?: return null

        val spyNames: Set<String>? = cursor.getNearestMessage(SPY_FIELD_NAMES_KEY)
        log.fine(
            "[PurgeMapstruct] Checking when-spy: candidate=${spyIdent.simpleName} known-spies=$spyNames",
        )
        if (spyNames == null || spyIdent.simpleName !in spyNames) return null

        val doStubName = if (stubName == "thenReturn") "doReturn" else "doThrow"
        val stubValue = invocation.arguments[0]

        val mockitoType = JavaType.buildType("org.mockito.Mockito") as JavaType.FullyQualified
        val stubberType = JavaType.buildType("org.mockito.stubbing.Stubber") as JavaType.FullyQualified

        val doStubMethodType =
            JavaType.Method(
                null,
                Flag.Public.bitMask or Flag.Static.bitMask,
                mockitoType,
                doStubName,
                stubberType,
                listOf("value"),
                listOf(stubValue.type ?: JavaType.buildType("java.lang.Object")),
                emptyList(),
                emptyList(),
                null,
                null,
            )

        val doStubIdent =
            J.Identifier(
                UUID.randomUUID(),
                Space.EMPTY,
                Markers.EMPTY,
                emptyList(),
                doStubName,
                doStubMethodType,
                null,
            )
        val doStubArgs: JContainer<Expression> =
            JContainer.build(
                Space.EMPTY,
                listOf(JRightPadded.build(stubValue.withPrefix(Space.EMPTY))),
                Markers.EMPTY,
            )
        val doStubCall =
            J.MethodInvocation(
                UUID.randomUUID(),
                invocation.prefix,
                invocation.markers,
                null,
                null,
                doStubIdent,
                doStubArgs,
                doStubMethodType,
            )

        val newWhenCall =
            J.MethodInvocation(
                UUID.randomUUID(),
                Space.EMPTY,
                whenInvocation.markers,
                JRightPadded.build(doStubCall as Expression),
                null,
                whenInvocation.name.withPrefix(Space.EMPTY),
                JContainer.build(
                    Space.EMPTY,
                    listOf(
                        JRightPadded.build(spyIdent.withPrefix(Space.EMPTY)),
                    ),
                    Markers.EMPTY,
                ),
                whenInvocation.methodType,
            )

        val spyArgs = spyMethodCall.arguments
        val paddedFinalArgs: List<JRightPadded<Expression>> =
            spyArgs.mapIndexed { i, arg ->
                val prefix = if (i == 0) Space.EMPTY else Space.SINGLE_SPACE
                JRightPadded.build(arg.withPrefix(prefix))
            }

        val finalCall =
            J.MethodInvocation(
                UUID.randomUUID(),
                Space.EMPTY,
                spyMethodCall.markers,
                JRightPadded.build(newWhenCall as Expression),
                null,
                spyMethodCall.name.withPrefix(Space.EMPTY),
                JContainer.build(Space.EMPTY, paddedFinalArgs, Markers.EMPTY),
                spyMethodCall.methodType,
            )

        doAfterVisit(AddImport("org.mockito.Mockito", doStubName, false))
        return finalCall
    }

    private companion object {
        private val log = Logger.getLogger(RewriteWhenOnSpy::class.java.name)

        /** Cursor message key: `Set<String>` of local field names annotated with Mockito's `@Spy`. */
        private const val SPY_FIELD_NAMES_KEY = "purgeMapstruct.spyFieldNames"

        private fun collectSpyFieldNames(cu: J.CompilationUnit): Set<String> =
            cu.classes
                .asSequence()
                .flatMap { it.body.statements }
                .filterIsInstance<J.VariableDeclarations>()
                .filter { stmt ->
                    stmt.leadingAnnotations.any { a ->
                        a.simpleName == "Spy" || TypeUtils.isOfClassType(a.type, "org.mockito.Spy")
                    }
                }.flatMap { it.variables }
                .map { it.simpleName }
                .toSet()
    }
}
