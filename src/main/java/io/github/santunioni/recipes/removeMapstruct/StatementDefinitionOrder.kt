package io.github.santunioni.recipes.removeMapstruct

import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.Statement

class StatementDefinitionOrder : Comparator<Statement?> {
    /**
     * Order statements: First fields, then public method, then protected methods, then private
     * methods
     *
     * @param first the first object to be compared.
     * @param second the second object to be compared.
     * @return the order
     */
    override fun compare(
        first: Statement?,
        second: Statement?,
    ): Int {
        val firstOrder = getOrder(first)
        val secondOrder = getOrder(second)
        return Integer.compare(firstOrder, secondOrder)
    }

    private fun getOrder(statement: Statement?): Int {
        if (statement is J.VariableDeclarations) {
            if (statement.hasModifier(J.Modifier.Type.Static)) {
                if (statement.hasModifier(J.Modifier.Type.Public)) {
                    return 10000000
                } else if (statement.hasModifier(J.Modifier.Type.Protected)) {
                    return 10100000
                } else {
                    return 10200000
                }
            } else {
                if (statement.hasModifier(J.Modifier.Type.Public)) {
                    return 11000000
                } else if (statement.hasModifier(J.Modifier.Type.Protected)) {
                    return 11100000
                } else {
                    return 11200000
                }
            }
        } else if (statement is J.MethodDeclaration) {
            // Constructor before every other method: check if the method name starts with a capital
            // letter
            if (statement.getSimpleName().matches("^[A-Z].*".toRegex())) {
                return 19999999
            } else if (statement.hasModifier(J.Modifier.Type.Static)) {
                if (statement.hasModifier(J.Modifier.Type.Public)) {
                    return 20000000
                } else if (statement.hasModifier(J.Modifier.Type.Protected)) {
                    return 20100000
                } else {
                    return 20200000
                }
            } else {
                if (statement.hasModifier(J.Modifier.Type.Public)) {
                    return 21000000
                } else if (statement.hasModifier(J.Modifier.Type.Protected)) {
                    return 21100000
                } else {
                    return 21200000
                }
            }
        }
        return 90000000
    }
}
