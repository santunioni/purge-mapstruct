package io.github.santunioni.recipes.inlineMapstruct.scanners

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeTree

internal interface MapstructRefsWriter {
    fun addLinking(
        superDecl: TypeTree,
        mapperImpl: J.CompilationUnit,
    )

    /** Index every visited compilation unit by the FQN of its top-level class. */
    fun registerCompilationUnit(cu: J.CompilationUnit)

    /** Record that [mapperFqn] is decorated by the hand-written class [decoratorFqn]. */
    fun addDecoratedMapper(
        mapperFqn: String,
        decoratorFqn: String,
    )
}
