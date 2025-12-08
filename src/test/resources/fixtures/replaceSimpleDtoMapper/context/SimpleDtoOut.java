package com.santunioni.fixtures.dtoMappers;

public class SimpleDtoOut {
    private final Long id;
    private final String name;

    public SimpleDtoOut(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
