package com.santunioni.recipes.removeMapstruct;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Comparator;

public class StatementDefinitionOrder implements Comparator<Statement> {
    /**
     * Order statements:
     * First fields, then public method, then protected methods, then private methods
     *
     * @param first  the first object to be compared.
     * @param second the second object to be compared.
     * @return the order
     */
    @Override
    public int compare(Statement first, Statement second) {
        int firstOrder = getOrder(first);
        int secondOrder = getOrder(second);
        return Integer.compare(firstOrder, secondOrder);
    }

    private int getOrder(Statement statement) {
        if (statement instanceof J.VariableDeclarations) {
            return 0;
        } else if (statement instanceof J.MethodDeclaration method) {
            if (method.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Public)) {
                return 1;
            } else if (method.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Protected)) {
                return 2;
            } else {
                return 3;
            }
        }
        return 4;
    }

}
