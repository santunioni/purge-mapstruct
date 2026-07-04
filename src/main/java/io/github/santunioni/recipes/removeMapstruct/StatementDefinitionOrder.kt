package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.Statement

class StatementDefinitionOrder : Comparator<Statement> {
  /**
   * Order statements: First fields, then public methods, then protected methods, then private
   * methods.
   */
  override fun compare(first: Statement, second: Statement): Int =
      getOrder(first).compareTo(getOrder(second))

  private fun getOrder(statement: Statement): Int =
      when (statement) {
        is J.VariableDeclarations ->
            if (statement.hasModifier(J.Modifier.Type.Static)) {
              when {
                statement.hasModifier(J.Modifier.Type.Public) -> 10_000_000
                statement.hasModifier(J.Modifier.Type.Protected) -> 10_100_000
                else -> 10_200_000
              }
            } else {
              when {
                statement.hasModifier(J.Modifier.Type.Public) -> 11_000_000
                statement.hasModifier(J.Modifier.Type.Protected) -> 11_100_000
                else -> 11_200_000
              }
            }
        is J.MethodDeclaration ->
            when {
              // Constructor before every other method (name starts with capital letter)
              statement.simpleName.matches(Regex("^[A-Z].*")) -> 19_999_999
              statement.hasModifier(J.Modifier.Type.Static) ->
                  when {
                    statement.hasModifier(J.Modifier.Type.Public) -> 20_000_000
                    statement.hasModifier(J.Modifier.Type.Protected) -> 20_100_000
                    else -> 20_200_000
                  }
              else ->
                  when {
                    statement.hasModifier(J.Modifier.Type.Public) -> 21_000_000
                    statement.hasModifier(J.Modifier.Type.Protected) -> 21_100_000
                    else -> 21_200_000
                  }
            }
        else -> 90_000_000
      }
}
