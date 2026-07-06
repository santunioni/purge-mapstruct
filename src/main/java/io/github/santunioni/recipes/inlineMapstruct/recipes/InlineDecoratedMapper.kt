package io.github.santunioni.recipes.inlineMapstruct.recipes

import io.github.santunioni.recipes.inlineMapstruct.getDecoratorFqn
import io.github.santunioni.recipes.inlineMapstruct.isDecoratedMapperDeclaration
import org.openrewrite.ExecutionContext
import org.openrewrite.internal.ListUtils
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType
import org.openrewrite.java.tree.Space
import org.openrewrite.java.tree.Statement
import org.openrewrite.java.tree.TypeUtils
import org.openrewrite.marker.Markers
import java.util.UUID
import java.util.logging.Logger

/**
 * Inlines a MapStruct `@DecoratedWith` mapper — a four-body structure (the `@Mapper` interface, the
 * hand-written decorator, the `@Primary` `FooMapperImpl` and the delegate `FooMapperImpl_`) — into a
 * single concrete class written onto the interface's source path.
 *
 * The result keeps the decorator/delegate split intact: the outer `FooMapper` class is the decorator
 * merged with the primary impl's pass-through methods, and the delegate becomes a nested `static`
 * `FooMapperDelegate` class. See TODO.md for the full rationale.
 */
@Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
internal class InlineDecoratedMapper(
    private val reader: MapstructRefsReader,
) : JavaVisitor<ExecutionContext>() {
    override fun visitCompilationUnit(
        mapperDeclFile: J.CompilationUnit,
        ctx: ExecutionContext,
    ): J {
        val superResult = super.visitCompilationUnit(mapperDeclFile, ctx)
        val visited = superResult as? J.CompilationUnit ?: return superResult
        if (!isDecoratedMapperDeclaration(visited)) return visited

        val mapperDeclClass = visited.classes[0]
        val mapperName = mapperDeclClass.name.simpleName
        val mapperFqn = mapperDeclClass.type?.fullyQualifiedName ?: return visited
        val decoratorFqn = getDecoratorFqn(visited) ?: return visited

        try {
            val decoratorCU = reader.getCompilationUnitByFqn(decoratorFqn) ?: return visited
            val delegateCU = reader.getImplementersOf(mapperFqn).firstOrNull() ?: return visited
            val primaryCU = reader.getImplementersOf(decoratorFqn).firstOrNull() ?: return visited

            val fqDecorator = decoratorCU.classes.firstOrNull() ?: return visited
            val fqPrimary = primaryCU.classes.firstOrNull() ?: return visited
            val fqDelegate = delegateCU.classes.firstOrNull() ?: return visited

            val nestedName = "${mapperName}Delegate"
            val nestedFqn = "$mapperFqn.$nestedName"
            val nestedType = JavaType.buildType(nestedFqn)

            val delegateMethodNames =
                fqDecorator.body.statements
                    .filterIsInstance<J.MethodDeclaration>()
                    .filter { it.body != null && !it.isConstructor }
                    .map { it.name.simpleName }
                    .toSet()

            // Overridden methods: keep the decorator's custom logic verbatim (minus @Override).
            val decoratorMethods =
                fqDecorator.body.statements
                    .filterIsInstance<J.MethodDeclaration>()
                    .filter { it.body != null && !it.isConstructor }
                    .map { stripOverride(it) }

            // Non-overridden methods: pull down the primary impl's pass-throughs (minus @Override).
            val passThroughMethods =
                fqPrimary.body.statements
                    .filterIsInstance<J.MethodDeclaration>()
                    .filter { it.body != null && !it.isConstructor && it.name.simpleName !in delegateMethodNames }
                    .map { stripOverride(it) }

            // The retyped, de-qualified delegate field taken from the decorator.
            val delegateField =
                fqDecorator.body.statements
                    .filterIsInstance<J.VariableDeclarations>()
                    .firstOrNull()
                    ?.let { retypeDelegateField(it, nestedName, nestedType) }
                    ?: return visited

            val nestedDelegate = buildNestedDelegate(fqDelegate, nestedName)

            val outerStatements: List<Statement> =
                listOf(delegateField) + decoratorMethods + passThroughMethods + listOf(nestedDelegate)

            val componentAnnotations =
                fqPrimary.leadingAnnotations.filter { it.simpleName == "Component" }

            val outer =
                fqDecorator
                    .withName(fqDecorator.name.withSimpleName(mapperName))
                    .withModifiers(fqDecorator.modifiers.filter { it.type != J.Modifier.Type.Abstract })
                    .withExtends(null)
                    .withImplements(null)
                    .withLeadingAnnotations(componentAnnotations)
                    .withBody(fqDecorator.body.withStatements(outerStatements))

            // Merge imports from all four sources (interface + decorator + both impls), dropping
            // MapStruct imports and de-duplicating. RemoveUnusedImports drops the leftovers post-merge.
            val mergedImports =
                (visited.imports + decoratorCU.imports + primaryCU.imports + delegateCU.imports)
                    .filter { !it.packageName.startsWith(MAPSTRUCT_GROUP) }
                    .distinctBy { "${it.isStatic}|${it.packageName}.${it.className}" }

            return visited
                .withImports(mergedImports)
                .withClasses(listOf(outer))
        } catch (e: Exception) {
            log.severe("Error processing decorated @Mapper ${mapperDeclClass.name}: ${e.message}")
            throw RuntimeException("Failed to inline decorated Mapstruct Mapper: $mapperName", e)
        }
    }

    private fun buildNestedDelegate(
        delegateClass: J.ClassDeclaration,
        nestedName: String,
    ): J.ClassDeclaration {
        val methods =
            delegateClass.body.statements.map { st ->
                if (st is J.MethodDeclaration) stripOverride(st) else st
            }
        val keptAnnotations = delegateClass.leadingAnnotations.filter { keepDelegateAnnotation(it) }
        return delegateClass
            .withName(delegateClass.name.withSimpleName(nestedName))
            .withImplements(null)
            .withExtends(null)
            .withModifiers(ensureStatic(delegateClass.modifiers))
            .withLeadingAnnotations(keptAnnotations)
            .withBody(delegateClass.body.withStatements(methods))
    }

    private fun retypeDelegateField(
        field: J.VariableDeclarations,
        nestedName: String,
        nestedType: JavaType?,
    ): J.VariableDeclarations {
        val keptAnnotations = field.leadingAnnotations.filter { it.simpleName != "Qualifier" }
        val typeExpr = field.typeExpression
        val newTypeExpr =
            J.Identifier(
                UUID.randomUUID(),
                typeExpr?.prefix ?: Space.SINGLE_SPACE,
                typeExpr?.markers ?: Markers.EMPTY,
                emptyList(),
                nestedName,
                nestedType,
                null,
            )
        return field
            .withLeadingAnnotations(keptAnnotations)
            .withTypeExpression(newTypeExpr)
    }

    private companion object {
        private val log = Logger.getLogger(InlineDecoratedMapper::class.java.name)
        private const val MAPSTRUCT_GROUP = "org.mapstruct"

        private fun keepDelegateAnnotation(a: J.Annotation): Boolean =
            !(
                a.simpleName == "Generated" ||
                    a.simpleName == "Qualifier" ||
                    a.simpleName == "Primary" ||
                    TypeUtils.isOfClassType(a.type, "javax.annotation.processing.Generated") ||
                    TypeUtils.isOfClassType(a.type, "jakarta.annotation.Generated")
            )

        private fun ensureStatic(modifiers: List<J.Modifier>): List<J.Modifier> {
            if (modifiers.any { it.type == J.Modifier.Type.Static }) return modifiers
            val staticModifier =
                J.Modifier(
                    UUID.randomUUID(),
                    Space.SINGLE_SPACE,
                    Markers.EMPTY,
                    null,
                    J.Modifier.Type.Static,
                    emptyList(),
                )
            return modifiers + staticModifier
        }

        private fun stripOverride(method: J.MethodDeclaration): J.MethodDeclaration {
            val prefixToPreserve =
                method.leadingAnnotations
                    .firstOrNull { ann ->
                        ann.simpleName == "Override" || TypeUtils.isOfClassType(ann.type, "java.lang.Override")
                    }?.prefix
            val filtered =
                ListUtils.filter(method.leadingAnnotations) { ann ->
                    ann.simpleName != "Override" && !TypeUtils.isOfClassType(ann.type, "java.lang.Override")
                } ?: emptyList()
            var result = method.withLeadingAnnotations(filtered)
            if (prefixToPreserve != null && filtered.isEmpty()) {
                result = result.withPrefix(prefixToPreserve)
            }
            return result
        }
    }
}
