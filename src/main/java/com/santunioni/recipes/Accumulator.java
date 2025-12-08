package com.santunioni.recipes;

import lombok.extern.java.Log;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Log
@NullMarked
public class Accumulator {
    Map<String, List<J.CompilationUnit>> mapSuperToItsImplementers = new HashMap<>();

    Map<String, String> mapImplementerToItsSup = new HashMap<>();

    void addLinking(TypeTree superDecl, J.CompilationUnit mapperImpl) {
        final String superFqn = Objects.requireNonNull(superDecl.getType()).toString();
        if (!mapSuperToItsImplementers.containsKey(superFqn)) {
            mapSuperToItsImplementers.put(superFqn, new ArrayList<>());
        }
        mapSuperToItsImplementers.get(superFqn).add(mapperImpl);
        JavaType.FullyQualified mapperImplFqn = mapperImpl.getClasses().get(0).getType();
        if (mapperImplFqn != null) {
            String implFqn = mapperImplFqn.getFullyQualifiedName();
            mapImplementerToItsSup.put(implFqn, superFqn);
        }
    }

    J.@Nullable CompilationUnit getImplementer(J.ClassDeclaration compilationUnit) {
        if (compilationUnit.getType() == null) {
            log.severe("Could not find fully qualified name for " + compilationUnit +
                    ". Skipping.");
            return null;
        }

        String fqn = compilationUnit.getType().getFullyQualifiedName();
        List<J.CompilationUnit> implementers = mapSuperToItsImplementers.get(fqn);

        if (implementers == null || implementers.size() != 1) {
            log.severe("Multiple or no generated implementations found for " + fqn + ". Skipping.");
            return null;
        }
        return implementers.get(0);
    }

    @Nullable String getSuperFqnFromImplFqn(String implFqn) {
        return mapImplementerToItsSup.get(implFqn);
    }

}

