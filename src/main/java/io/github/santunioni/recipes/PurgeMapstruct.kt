package io.github.santunioni.recipes

import io.github.santunioni.recipes.removeMapstruct.Accumulator
import io.github.santunioni.recipes.removeMapstruct.ImplementationScanner
import io.github.santunioni.recipes.removeMapstruct.MapperProcessor
import org.openrewrite.ExecutionContext
import org.openrewrite.ScanningRecipe
import org.openrewrite.TreeVisitor

/**
 * Applies cleanup visitors only to files that [MapperProcessor] actually changes.
 *
 * Files that [MapperProcessor] does not touch (unrelated services, DTOs, etc.) are
 * returned unchanged — keeping the diff of a purge PR as small as possible.
 */
class PurgeMapstruct : ScanningRecipe<Accumulator>() {
    override fun getDisplayName(): String = "Purge MapStruct — cleaner code"

    override fun getDescription(): String =
        "Inlines every @Mapper interface/abstract class into plain Java, then applies " +
            "a curated set of cleanup and formatting recipes — but only to the files it changes."

    override fun getInitialValue(ctx: ExecutionContext): Accumulator = Accumulator()

    override fun getScanner(acc: Accumulator): TreeVisitor<*, ExecutionContext> = ImplementationScanner(acc)

    override fun getVisitor(acc: Accumulator): TreeVisitor<*, ExecutionContext> = MapperProcessor(acc)
}
