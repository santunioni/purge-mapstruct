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
        if (statement instanceof J.VariableDeclarations variable) {
            if (variable.hasModifier(J.Modifier.Type.Static)) {
                if (variable.hasModifier(J.Modifier.Type.Public)) {
                    return 10000000;
                } else if (variable.hasModifier(J.Modifier.Type.Protected)) {
                    return 10100000;
                } else {
                    return 10200000;
                }
            } else {
                if (variable.hasModifier(J.Modifier.Type.Public)) {
                    return 11000000;
                } else if (variable.hasModifier(J.Modifier.Type.Protected)) {
                    return 11100000;
                } else {
                    return 11200000;
                }
            }
        } else if (statement instanceof J.MethodDeclaration method) {
            if (method.hasModifier(J.Modifier.Type.Static)) {
                if (method.hasModifier(J.Modifier.Type.Public)) {
                    return 20000000;
                } else if (method.hasModifier(J.Modifier.Type.Protected)) {
                    return 20100000;
                } else {
                    return 20200000;
                }
            } else {
                if (method.hasModifier(J.Modifier.Type.Public)) {
                    return 21000000;
                } else if (method.hasModifier(J.Modifier.Type.Protected)) {
                    return 21100000;
                } else {
                    return 21200000;
                }
            }
        }
        return 90000000;
    }

}
