package io.github.santunioni.recipes.removeMapstruct

import java.util.logging.Logger
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType
import org.openrewrite.java.tree.TypeTree

class Accumulator {
    private val mapSuperToItsImplementers: MutableMap<String, MutableList<J.CompilationUnit>> =
        HashMap()
    private val mapImplementerToItsSup: MutableMap<String, String> = HashMap()

    fun addLinking(superDecl: TypeTree, mapperImpl: J.CompilationUnit) {
        val superFqn = checkNotNull(superDecl.type).toString()
        mapSuperToItsImplementers.getOrPut(superFqn) { mutableListOf() }.add(mapperImpl)
        val mapperImplFqn = mapperImpl.classes[0].type
        if (mapperImplFqn != null) {
            mapImplementerToItsSup[mapperImplFqn.fullyQualifiedName] = superFqn
        }
    }

    fun getImplementer(compilationUnit: J.ClassDeclaration): J.CompilationUnit? {
        val type = compilationUnit.type
        if (type == null) {
            log.severe("Could not find fully qualified name for $compilationUnit. Skipping.")
            return null
        }
        val fqn = type.fullyQualifiedName
        val implementers = mapSuperToItsImplementers[fqn]
        if (implementers == null || implementers.size != 1) {
            log.severe("Multiple or no generated implementations found for $fqn. Skipping.")
            return null
        }
        return implementers[0]
    }

    fun getSuperFqnFromImplFqn(implFqn: String): String? = mapImplementerToItsSup[implFqn]

    companion object {
        private val log = Logger.getLogger(Accumulator::class.java.name)
    }
}
