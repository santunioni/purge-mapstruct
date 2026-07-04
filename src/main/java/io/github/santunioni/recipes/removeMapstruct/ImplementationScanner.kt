package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J

class ImplementationScanner(
    private val acc: Accumulator,
) : JavaIsoVisitor<ExecutionContext>() {
    override fun visitCompilationUnit(
        mapperImpl: J.CompilationUnit,
        ctx: ExecutionContext,
    ): J.CompilationUnit {
        if (!isMapperImplementation(mapperImpl)) {
            return mapperImpl
        }
        if (mapperImpl.packageDeclaration == null) {
            return super.visitCompilationUnit(mapperImpl, ctx)
        }

        mapperImpl.classes.forEach { classDecl ->
            classDecl.implements.orEmpty().forEach { acc.addLinking(it, mapperImpl) }
            classDecl.extends?.let { acc.addLinking(it, mapperImpl) }
        }

        return super.visitCompilationUnit(mapperImpl, ctx)
    }
}
