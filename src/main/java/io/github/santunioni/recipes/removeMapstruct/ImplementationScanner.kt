package io.github.santunioni.recipes.removeMapstruct

import io.github.santunioni.recipes.removeMapstruct.Functions.isMapperImplementation
import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J

class ImplementationScanner(private val acc: Accumulator) : JavaIsoVisitor<ExecutionContext>() {
  override fun visitCompilationUnit(
      mapperImpl: J.CompilationUnit,
      ctx: ExecutionContext,
  ): J.CompilationUnit {
    if (!isMapperImplementation(mapperImpl)) {
      return mapperImpl
    }

    for (classDecl in mapperImpl.classes) {
      if (mapperImpl.packageDeclaration == null) continue

      for (interfaceDecl in classDecl.implements.orEmpty()) {
        acc.addLinking(interfaceDecl, mapperImpl)
      }

      classDecl.extends?.let { acc.addLinking(it, mapperImpl) }
    }
    return super.visitCompilationUnit(mapperImpl, ctx)
  }
}
