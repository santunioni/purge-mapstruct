package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.Expression
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType
import org.openrewrite.java.tree.TypeTree
import java.util.UUID

/**
 * Rewrites every reference to a MapStruct-generated implementation type (`FooMapperImpl`) back to the
 * original mapper type (`FooMapper`): imports, `new FooMapperImpl()`, variable/parameter types,
 * `FooMapperImpl.class` field accesses, and `instanceof` checks.
 *
 * Driven entirely by the scan-pass linkings in [MapstructRefsReader] (`impl FQN → super FQN`), so it is
 * safe to run both broadly across every file (unrelated call sites still referencing the impl) and on
 * the merged class produced by [MapperProcessorBare].
 */
class RewriteImplReferences(
    private val mapstructRefsReader: MapstructRefsReader,
) : JavaVisitor<ExecutionContext>() {
    /**
     * Replaces references like `UserMapperImpl.class` to `UserMapper.class`, for both simple-name and
     * fully-qualified targets.
     */
    override fun visitFieldAccess(
        fieldAccess_: J.FieldAccess,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitFieldAccess(fieldAccess_, ctx)
        val visited = superResult as? J.FieldAccess ?: return superResult

        return when (val target = visited.target) {
            // Case A: simple-name target, e.g. `UserMapperImpl.class`
            is J.Identifier -> {
                val targetFqn = target.type?.toString() ?: return visited
                val superFqn = mapstructRefsReader.getSuperFqnFromImplFqn(targetFqn) ?: return visited
                val superType = JavaType.buildType(superFqn)
                visited
                    .withTarget(
                        J.Identifier(
                            UUID.randomUUID(),
                            target.prefix,
                            target.markers,
                            emptyList(),
                            extractSimpleName(superFqn),
                            superType,
                            target.fieldType,
                        ),
                    ).withType(superType)
            }

            // Case B: fully qualified target, e.g. `com.foo.UserMapperImpl.class`
            is J.FieldAccess -> {
                val targetFqn = extractFqnFromFieldAccess(target)
                val superFqn = mapstructRefsReader.getSuperFqnFromImplFqn(targetFqn) ?: return visited
                val superType = JavaType.buildType(superFqn)
                visited.withTarget(
                    target.withName(target.name.withSimpleName(extractSimpleName(superFqn))).withType(superType),
                )
            }

            else -> {
                visited
            }
        }
    }

    /** Replaces import references of `UserMapperImpl` to `UserMapper`. */
    override fun visitImport(
        imp: J.Import,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitImport(imp, ctx)
        val visited = superResult as? J.Import ?: return superResult

        val importFqn = extractFqnFromFieldAccess(visited.qualid)
        val superFqn = mapstructRefsReader.getSuperFqnFromImplFqn(importFqn) ?: return visited

        if (importFqn == superFqn) return visited

        val currentSimpleName = getFinalIdentifierName(visited.qualid)
        val expectedSimpleName = extractSimpleName(superFqn)
        if (currentSimpleName == expectedSimpleName && !currentSimpleName.endsWith("Impl")) {
            return visited
        }

        return replaceImportQualid(visited, superFqn)
    }

    /** Replaces instantiations of `UserMapperImpl()` to `UserMapper()`. */
    override fun visitNewClass(
        newClass: J.NewClass,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitNewClass(newClass, ctx)
        val visited = superResult as? J.NewClass ?: return superResult

        val clazz = visited.clazz ?: return visited
        val replacedClazz = replaceTypeTreeIfNeeded(clazz)
        if (replacedClazz === clazz) return visited
        return visited.withClazz(replacedClazz)
    }

    /** Replaces variable declarations like `UserMapperImpl userMapper` to `UserMapper userMapper`. */
    override fun visitVariableDeclarations(
        multiVariable: J.VariableDeclarations,
        p: ExecutionContext,
    ): J {
        val superResult = super.visitVariableDeclarations(multiVariable, p)
        val visited = superResult as? J.VariableDeclarations ?: return superResult

        val typeExpression = visited.typeExpression ?: return visited
        val replacedTypeExpression = replaceTypeTreeIfNeeded(typeExpression)
        if (replacedTypeExpression === typeExpression) return visited
        return visited.withTypeExpression(replacedTypeExpression)
    }

    /**
     * Replaces instanceof checks like `userMapper instanceof UserMapperImpl` to `userMapper
     * instanceof UserMapper`.
     */
    @Suppress("UNCHECKED_CAST")
    override fun visitInstanceOf(
        instanceOf_: J.InstanceOf,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitInstanceOf(instanceOf_, ctx)
        val visited = superResult as? J.InstanceOf ?: return superResult

        val clazzExpr = visited.clazz as? J.ControlParentheses<*> ?: return visited
        val treeObj = clazzExpr.tree as? TypeTree ?: return visited

        val replacedClazz = replaceTypeTreeIfNeeded(treeObj)
        if (replacedClazz === treeObj) return visited

        // Cast required because J.ControlParentheses<*> (star projection) blocks withTree().
        return visited.withClazz((clazzExpr as J.ControlParentheses<TypeTree>).withTree(replacedClazz))
    }

    private fun extractFqnFromFieldAccess(fieldAccess: J.FieldAccess): String {
        val parts =
            buildList {
                add(fieldAccess.name.simpleName)
                var current: Expression = fieldAccess.target
                while (current is J.FieldAccess) {
                    add(current.name.simpleName)
                    current = current.target
                }
                if (current is J.Identifier) add(current.simpleName)
            }
        return parts.reversed().joinToString(".")
    }

    private fun replaceTypeTreeIfNeeded(typeTree: TypeTree): TypeTree {
        val type = typeTree.type as? JavaType.FullyQualified ?: return typeTree
        val typeFqn = type.fullyQualifiedName
        val superFqn = mapstructRefsReader.getSuperFqnFromImplFqn(typeFqn) ?: return typeTree
        val superSimpleName = extractSimpleName(superFqn)
        val superType = JavaType.buildType(superFqn)
        return J.Identifier(
            UUID.randomUUID(),
            typeTree.prefix,
            typeTree.markers,
            emptyList(),
            superSimpleName,
            superType,
            null,
        )
    }

    private fun replaceImportQualid(
        import_: J.Import,
        superFqn: String,
    ): J.Import {
        val superSimpleName = extractSimpleName(superFqn)
        val qualid = import_.qualid
        val newQualid = qualid.withName(qualid.name.withSimpleName(superSimpleName))
        return import_.withQualid(newQualid)
    }

    private fun extractSimpleName(fqn: String): String = fqn.substringAfterLast('.')

    private fun getFinalIdentifierName(fieldAccess: J.FieldAccess): String =
        generateSequence(fieldAccess) { it.target as? J.FieldAccess }.last().name.simpleName
}
