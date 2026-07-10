package io.github.santunioni.recipes

import org.openrewrite.java.Assertions.java
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpecs
import java.nio.charset.StandardCharsets

class RecipeTestSpecification {
    companion object {
        private fun readResource(resource: String): String =
            PurgeMapstructTest::class.java.classLoader.getResourceAsStream(resource)!!.use { stream ->
                String(stream.readAllBytes(), StandardCharsets.UTF_8)
            }
    }

    val sourceSpecs = mutableListOf<SourceSpecs>()

    fun include(file: String) {
        sourceSpecs.add(
            java(
                readResource(file),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/${file.split("/").last()}")
            },
        )
    }

    fun transform(
        from: String,
        to: String,
    ) {
        sourceSpecs.add(
            java(
                readResource(from),
                readResource(to),
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/${from.split("/").last()}")
            },
        )
    }

    fun delete(file: String) {
        sourceSpecs.add(
            java(
                readResource(file),
                null,
            ) { spec ->
                spec.path("src/main/java/com/santunioni/fixtures/${file.split("/").last()}")
            },
        )
    }
}

operator fun RewriteTest.invoke(action: RecipeTestSpecification.() -> Unit) {
    val ctx = RecipeTestSpecification()
    action(ctx)
    rewriteRun(*ctx.sourceSpecs.toTypedArray())
}
