package com.santunioni.recipes.removeMapstruct;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

class Functions {
    static boolean isMapperImplementation(J.CompilationUnit compilationUnit) {
        final Optional<J.Annotation> generatedAnnotationOpt = compilationUnit.getClasses().stream()
                .flatMap(cd -> {
                    if (!((cd.getImplements() != null
                            && !cd.getImplements().isEmpty()) || cd.getExtends() != null)) {
                        return Stream.empty();
                    }

                    return cd.getLeadingAnnotations().stream().filter(an -> TypeUtils.isOfClassType(
                            an.getType(),
                            "javax.annotation.processing.Generated"));
                }).findFirst();

        if (generatedAnnotationOpt.isPresent()) {
            final var generatedAnnotation = generatedAnnotationOpt.get();
            for (var arg : Objects.requireNonNullElse(generatedAnnotation.getArguments(), Collections.emptyList())) {
                if (arg instanceof J.Assignment argAssignment
                        && argAssignment.getAssignment().toString().startsWith("org.mapstruct")) {
                    return true;
                }
            }
        }

        return false;
    }

    static boolean isMapperDeclaration(J.CompilationUnit originalCu) {
        return originalCu.getClasses().stream()
                .anyMatch(cd -> cd.getLeadingAnnotations().stream()
                        .anyMatch(a -> a.getType() != null && TypeUtils.isOfClassType(
                                a.getType(), "org.mapstruct.Mapper")));
    }
}
