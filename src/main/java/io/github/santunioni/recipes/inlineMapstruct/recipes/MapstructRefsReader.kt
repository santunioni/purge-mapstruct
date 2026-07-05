package io.github.santunioni.recipes.inlineMapstruct.recipes

import org.openrewrite.java.tree.J

interface MapstructRefsReader {
    fun getImplementer(compilationUnit: J.ClassDeclaration): J.CompilationUnit?

    fun getSuperFqnFromImplFqn(implFqn: String): String?
}
