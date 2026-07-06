package io.github.santunioni.recipes.inlineMapstruct.recipes

import org.openrewrite.java.tree.J

internal interface MapstructRefsReader {
    fun getImplementer(compilationUnit: J.ClassDeclaration): J.CompilationUnit?

    fun getSuperFqnFromImplFqn(implFqn: String): String?

    /** All generated implementations linked (via `implements`/`extends`) to [superFqn]. */
    fun getImplementersOf(superFqn: String): List<J.CompilationUnit>

    /** The compilation unit whose top-level class has this FQN, if scanned. */
    fun getCompilationUnitByFqn(fqn: String): J.CompilationUnit?

    /** FQNs of hand-written decorator classes referenced by `@DecoratedWith`. */
    fun getDecoratorFqns(): Set<String>
}
