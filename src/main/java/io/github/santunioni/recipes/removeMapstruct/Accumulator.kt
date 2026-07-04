package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeTree
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class Accumulator {
    private val mapSuperToItsImplementers: MutableMap<String, MutableList<J.CompilationUnit>> =
        HashMap()
    private val mapImplementerToItsSup: MutableMap<String, String> = HashMap()

    /** Source paths of files that MapperProcessor successfully merged during the edit pass. */
    val touchedSourcePaths: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    fun addLinking(
        superDecl: TypeTree,
        mapperImpl: J.CompilationUnit,
    ) {
        val superFqn = checkNotNull(superDecl.type).toString()
        mapSuperToItsImplementers.getOrPut(superFqn) { mutableListOf() }.add(mapperImpl)
        val mapperImplFqn = mapperImpl.classes[0].type
        if (mapperImplFqn != null) {
            mapImplementerToItsSup[mapperImplFqn.fullyQualifiedName] = superFqn
        }
    }

    fun getImplementer(compilationUnit: J.ClassDeclaration): J.CompilationUnit? {
        val type = compilationUnit.type ?: return null
        val implementers = mapSuperToItsImplementers[type.fullyQualifiedName] ?: return null

        if (implementers.size != 1) {
            log.severe(
                "${implementers.size} generated implementations found for ${type.fullyQualifiedName}. " +
                    "I was expecting a single one. Skipping.",
            )
            return null
        }

        return implementers.first()
    }

    fun getSuperFqnFromImplFqn(implFqn: String): String? = mapImplementerToItsSup[implFqn]

    companion object {
        private val log = Logger.getLogger(Accumulator::class.java.name)
    }
}
