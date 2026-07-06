package io.github.santunioni.recipes.inlineMapstruct

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.TypeUtils

internal fun isMapperImplementation(compilationUnit: J.CompilationUnit): Boolean {
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

internal fun isMapperDeclaration(originalCu: J): Boolean =
    originalCu is J.CompilationUnit &&
        originalCu.classes.any { cd ->
            cd.leadingAnnotations.any { a ->
                a.type != null && TypeUtils.isOfClassType(a.type, "org.mapstruct.Mapper")
            }
        }

/**
 * The fully-qualified name of the decorator class referenced by an `@DecoratedWith(Foo.class)`
 * annotation on the mapper declaration, or `null` when the mapper is not decorated.
 */
internal fun getDecoratorFqn(cu: J.CompilationUnit): String? {
    val mapperClass = cu.classes.firstOrNull() ?: return null
    val decoratedWith =
        mapperClass.leadingAnnotations.firstOrNull { a ->
            TypeUtils.isOfClassType(a.type, "org.mapstruct.DecoratedWith")
        } ?: return null
    val classLiteral = decoratedWith.arguments?.firstOrNull() as? J.FieldAccess ?: return null
    return TypeUtils.asFullyQualified(classLiteral.target.type)?.fullyQualifiedName
}

/** A mapper declaration (`@Mapper`) that also carries `@DecoratedWith(...)`. */
internal fun isDecoratedMapperDeclaration(cu: J.CompilationUnit): Boolean = isMapperDeclaration(cu) && getDecoratorFqn(cu) != null
