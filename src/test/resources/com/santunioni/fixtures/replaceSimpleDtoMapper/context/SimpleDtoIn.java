package com.santunioni.fixtures.dtoMappers;

public class SimpleDtoIn {
    private final Long id;
    private final String name;

    public SimpleDtoIn(Long id, String name) {
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
