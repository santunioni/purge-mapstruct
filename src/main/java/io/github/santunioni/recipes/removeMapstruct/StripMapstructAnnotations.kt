package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.ExecutionContext
import org.openrewrite.Tree
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType

/**
 * Removes MapStruct annotations (`@Mapper`, `@Mapping`, `@MappingTarget`, …) wherever they appear —
 * on types, methods, and parameters. Returning `null` from [visit] for an annotation drops it from
 * its enclosing list.
 *
 * Applied by [InlineMapstruct] to the merged class so the inlined result no longer depends on
 * MapStruct. When the annotation type is resolved it is matched by its `org.mapstruct` package;
 * otherwise it falls back to a simple-name allow-list (generated fixtures sometimes lose type
 * attribution).
 */
class StripMapstructAnnotations : JavaVisitor<ExecutionContext>() {
    override fun visit(
        tree: Tree?,
        ctx: ExecutionContext,
    ): J? {
        if (tree is J.Annotation && isMapstructAnnotation(tree)) {
            return null
        }
        return super.visit(tree, ctx)
    }

    private companion object {
        private const val MAPSTRUCT_GROUP = "org.mapstruct"

        private val MAPSTRUCT_ANNOTATION_SIMPLE_NAMES =
            setOf(
                "AfterMapping",
                "BeanMapping",
                "BeforeMapping",
                "Condition",
                "Context",
                "DecoratedWith",
                "EnumMapping",
                "InheritConfiguration",
                "InheritInverseConfiguration",
                "IterableMapping",
                "MapMapping",
                "Mapper",
                "MapperConfig",
                "Mapping",
                "MappingConstants",
                "Mappings",
                "MappingTarget",
                "Named",
                "ObjectFactory",
                "Qualifier",
                "SubclassMapping",
                "SubclassMappings",
                "TargetType",
                "ValueMapping",
                "ValueMappings",
            )

        private fun isMapstructAnnotation(a: J.Annotation): Boolean {
            val type = a.type
            return when {
                type != null && type !is JavaType.Unknown -> type.toString().startsWith(MAPSTRUCT_GROUP)
                else -> a.simpleName in MAPSTRUCT_ANNOTATION_SIMPLE_NAMES
            }
        }
    }
}
