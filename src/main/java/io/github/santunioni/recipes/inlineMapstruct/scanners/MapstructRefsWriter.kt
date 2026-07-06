package io.github.santunioni.recipes.inlineMapstruct.scanners

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeTree

internal interface MapstructRefsWriter {
    fun addLinking(
        superDecl: TypeTree,
        mapperImpl: J.CompilationUnit,
    )
}
