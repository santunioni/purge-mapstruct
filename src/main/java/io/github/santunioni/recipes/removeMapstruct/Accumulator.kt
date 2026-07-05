package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeTree
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

interface AccumulatorWriter {
    fun addLinking(
        superDecl: TypeTree,
        mapperImpl: J.CompilationUnit,
    )
}

interface AccumulatorReader {
    fun getImplementer(compilationUnit: J.ClassDeclaration): J.CompilationUnit?

    fun getSuperFqnFromImplFqn(implFqn: String): String?
}

class Accumulator :
    AccumulatorWriter,
    AccumulatorReader {
    private val mapSuperToItsImplementers: MutableMap<String, MutableList<J.CompilationUnit>> =
        HashMap()
    private val mapImplementerToItsSup: MutableMap<String, String> = HashMap()

    override fun addLinking(
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

    override fun getImplementer(compilationUnit: J.ClassDeclaration): J.CompilationUnit? {
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

    override fun getSuperFqnFromImplFqn(implFqn: String): String? = mapImplementerToItsSup[implFqn]

    companion object {
        private val log = Logger.getLogger(Accumulator::class.java.name)
    }
}
