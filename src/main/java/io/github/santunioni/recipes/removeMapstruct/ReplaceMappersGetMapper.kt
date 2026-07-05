package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.Expression
import org.openrewrite.java.tree.Flag
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JContainer
import org.openrewrite.java.tree.JRightPadded
import org.openrewrite.java.tree.JavaType
import org.openrewrite.java.tree.Space
import org.openrewrite.java.tree.TypeTree
import org.openrewrite.java.tree.TypeUtils
import org.openrewrite.marker.Markers
import java.util.UUID

/**
 * Replaces `Mappers.getMapper(SomeMapper.class)` with a direct constructor call `new SomeMapper()`.
 *
 * Stateless — depends on nothing from the scan pass, so it is safe to run both before inlining
 * (to rewrite references in the mapper declaration and in unrelated call sites) and after inlining
 * (to rewrite references that were copied in from the generated implementation).
 */
class ReplaceMappersGetMapper : JavaVisitor<ExecutionContext>() {
    override fun visitMethodInvocation(
        method: J.MethodInvocation,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitMethodInvocation(method, ctx)
        val visited = superResult as? J.MethodInvocation ?: return superResult

        if (!isMappersGetMapper(visited) || visited.arguments.size != 1) return visited

        val arg0 = visited.arguments[0]
        if (arg0 !is J.FieldAccess || arg0.name.simpleName != "class") return visited
        val classLiteral: J.FieldAccess = arg0

        val mapperType: TypeTree =
            when (val target = classLiteral.target) {
                is J.Identifier -> target.withPrefix(Space.SINGLE_SPACE)
                is J.FieldAccess -> target.withPrefix(Space.SINGLE_SPACE)
                else -> return visited
            }

        val mapperFqn = TypeUtils.asFullyQualified(mapperType.type) ?: return visited

        val constructorType =
            JavaType.Method(
                null,
                Flag.Public.bitMask,
                mapperFqn,
                "<constructor>",
                mapperFqn,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                null,
                null,
            )

        val noArguments: Expression = J.Empty(UUID.randomUUID(), Space.EMPTY, Markers.EMPTY)
        val arguments: JContainer<Expression> =
            JContainer.build(
                Space.EMPTY,
                listOf(JRightPadded.build(noArguments)),
                Markers.EMPTY,
            )

        return J.NewClass(
            UUID.randomUUID(),
            visited.prefix,
            visited.markers,
            null,
            Space.EMPTY,
            mapperType,
            arguments,
            null,
            constructorType,
        )
    }

    private companion object {
        private fun isMappersGetMapper(invocation: J.MethodInvocation): Boolean {
            if (invocation.simpleName != "getMapper") return false
            val declaringType = invocation.methodType?.declaringType
            return when {
                declaringType != null -> {
                    declaringType.fullyQualifiedName == "org.mapstruct.factory.Mappers"
                }

                else -> {
                    when (val select = invocation.select) {
                        is J.Identifier -> select.simpleName == "Mappers"
                        is J.FieldAccess -> select.name.simpleName == "Mappers"
                        else -> false
                    }
                }
            }
        }
    }
}
