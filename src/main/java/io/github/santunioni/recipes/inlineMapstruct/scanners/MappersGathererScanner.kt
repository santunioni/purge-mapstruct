package io.github.santunioni.recipes.inlineMapstruct.scanners

import io.github.santunioni.recipes.inlineMapstruct.getDecoratorFqn
import io.github.santunioni.recipes.inlineMapstruct.isMapperImplementation
import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J

internal class MappersGathererScanner internal constructor(
    private val acc: MapstructRefsWriter,
) : JavaIsoVisitor<ExecutionContext>() {
    override fun visitCompilationUnit(
        cu: J.CompilationUnit,
        ctx: ExecutionContext,
    ): J.CompilationUnit {
        // Index every compilation unit by FQN so the decorated-mapper merge can locate the
        // hand-written decorator and both generated impls by name later.
        acc.registerCompilationUnit(cu)

        // Record decorated mappers: `@Mapper @DecoratedWith(FooMapperDecorator.class)`.
        getDecoratorFqn(cu)?.let { decoratorFqn ->
            cu.classes.firstOrNull()?.type?.fullyQualifiedName?.let { mapperFqn ->
                acc.addDecoratedMapper(mapperFqn, decoratorFqn)
            }
        }

        if (!isMapperImplementation(cu)) {
            return super.visitCompilationUnit(cu, ctx)
        }
        if (cu.packageDeclaration == null) {
            return super.visitCompilationUnit(cu, ctx)
        }

        cu.classes.forEach { classDecl ->
            classDecl.implements.orEmpty().forEach { acc.addLinking(it, cu) }
            classDecl.extends?.let { acc.addLinking(it, cu) }
        }

        return super.visitCompilationUnit(cu, ctx)
    }
}
