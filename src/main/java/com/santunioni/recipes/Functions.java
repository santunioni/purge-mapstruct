package com.santunioni.recipes;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

class Functions {
     static boolean isMapperImplementation(J.CompilationUnit compilationUnit) {
        return compilationUnit.getClasses().stream()
                .anyMatch(cd -> {
                    String className = cd.getName().getSimpleName();
                    return className.endsWith("Impl")
                            && ((cd.getImplements() != null
                            && !cd.getImplements().isEmpty()) || cd.getExtends() != null)
                            && cd.getLeadingAnnotations().stream().anyMatch(an ->
                            TypeUtils.isOfClassType(an.getType(), "javax.annotation.processing.Generated"));
                });
    }

     static boolean isMapperDeclaration(J.CompilationUnit originalCu) {
        return originalCu.getClasses().stream()
                .anyMatch(cd -> cd.getAllAnnotations().stream()
                        .anyMatch(a -> (a.getType() != null && TypeUtils.isOfClassType(a.getType(), "org" +
                                ".mapstruct.Mapper"))
                                || a.getSimpleName().equals("Mapper")));
    }
}
