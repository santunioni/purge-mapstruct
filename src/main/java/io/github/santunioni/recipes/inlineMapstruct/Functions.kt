package io.github.santunioni.recipes.inlineMapstruct

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeUtils

fun isMapperImplementation(compilationUnit: J.CompilationUnit): Boolean {
    val generatedAnnotation =
        compilationUnit.classes
            .asSequence()
            .filter { cd -> (cd.implements?.isNotEmpty() == true) || cd.extends != null }
            .flatMap { cd -> cd.leadingAnnotations.asSequence() }
            .firstOrNull { an ->
                TypeUtils.isOfClassType(an.type, "javax.annotation.processing.Generated")
            } ?: return false

    return generatedAnnotation.arguments.orEmpty().any { arg ->
        arg is J.Assignment && arg.assignment.toString().startsWith("org.mapstruct")
    }
}

fun isMapperDeclaration(originalCu: J): Boolean =
    originalCu is J.CompilationUnit &&
        originalCu.classes.any { cd ->
            cd.leadingAnnotations.any { a ->
                a.type != null && TypeUtils.isOfClassType(a.type, "org.mapstruct.Mapper")
            }
        }
