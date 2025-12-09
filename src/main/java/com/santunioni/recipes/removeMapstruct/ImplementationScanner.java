package com.santunioni.recipes.removeMapstruct;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.santunioni.recipes.removeMapstruct.Functions.isMapperImplementation;

@NullMarked
public class ImplementationScanner extends JavaIsoVisitor<ExecutionContext> {
    private final Accumulator acc;

    public ImplementationScanner(Accumulator acc) {
        this.acc = acc;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit mapperImpl, ExecutionContext ctx) {
        if (!isMapperImplementation(mapperImpl)) {
            return mapperImpl;
        }

        for (J.ClassDeclaration classDecl : mapperImpl.getClasses()) {
            if (mapperImpl.getPackageDeclaration() == null) {
                continue;
            }

            List<TypeTree> implInterfaces = Objects
                    .requireNonNullElse(classDecl.getImplements(),
                            Collections.emptyList());
            for (TypeTree interfaceDecl : implInterfaces) {
                acc.addLinking(interfaceDecl, mapperImpl);
            }

            if (classDecl.getExtends() != null) {
                acc.addLinking(classDecl.getExtends(), mapperImpl);
            }

        }
        return super.visitCompilationUnit(mapperImpl, ctx);
    }

}
