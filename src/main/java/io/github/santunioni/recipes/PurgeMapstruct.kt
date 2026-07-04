package io.github.santunioni.recipes

import io.github.santunioni.recipes.removeMapstruct.Accumulator
import io.github.santunioni.recipes.removeMapstruct.ImplementationScanner
import io.github.santunioni.recipes.removeMapstruct.MapperProcessor
import org.openrewrite.ExecutionContext
import org.openrewrite.ScanningRecipe
import org.openrewrite.TreeVisitor

/**
 * PurgeMapstruct is a recipe designed to refactor Mapstruct mapper interfaces.
 *
 * It replaces @Mapper interfaces with their associated generated implementation. This process
 * includes managing necessary imports, removing @Override annotations from methods, and renaming
 * the generated implementation class to match the original mapper interface name.
 *
 * The recipe performs the following key steps:
 * <ol>
 *      <li>1. Identifies classes annotated with Mapstruct's @Mapper annotation.</li>
 *      <li>2. Locates the corresponding Mapstruct-generated implementation class (e.g., `MyMapperImpl`) from the source files in context.</li>
 *      <li>2. Merges imports from the original interface into the implementation class.</li>
 *      <li>3. Removes unnecessary annotations (such as @Override from methods and @Generated from classes) from the implementation class.</li>
 *      <li>4. Renames the implementation class to match the original interface name and removes "implements" declarations.</li>
 *      <li>5. Deletes the original Mapstruct-generated implementation source file, since its content has been merged into the original mapper file.</li>
 * </ol>
 *
 * This recipe assumes that the generated implementation is available in the source files being
 * processed. The Gradle plugin should be configured to include generated sources in the context.
 *
 * Note: This recipe copies default methods, static methods, and static fields from the interface to
 * the implementation class, removing the default modifier and preserving the static modifier.
 *
 * It is recommended to run supplementary cleanup tools or recipes (e.g., RemoveUnusedImports)
 * following this recipe to handle any redundant imports or formatting inconsistencies introduced
 * during the process.
 */
class PurgeMapstruct : ScanningRecipe<Accumulator>() {
    override fun getDisplayName(): String = "Replace MapStruct interface with implementation"

    override fun getDescription(): String =
        "Replaces @Mapper interfaces with their generated implementation. Copies imports and removes @Override" +
            " annotations from methods and @Generated annotations from classes. Copies default methods, " +
            "static methods, and static fields from the interface."

    override fun getInitialValue(ctx: ExecutionContext): Accumulator = Accumulator()

    override fun getScanner(acc: Accumulator): TreeVisitor<*, ExecutionContext> = ImplementationScanner(acc)

    override fun getVisitor(acc: Accumulator): TreeVisitor<*, ExecutionContext> = MapperProcessor(acc)
}
