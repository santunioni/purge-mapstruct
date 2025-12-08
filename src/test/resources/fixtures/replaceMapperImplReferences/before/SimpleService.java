package com.santunioni.fixtures.services;

import com.santunioni.fixtures.dtoMappers.SimpleDtoIn;
import com.santunioni.fixtures.dtoMappers.SimpleDtoMapperImpl;
import com.santunioni.fixtures.dtoMappers.SimpleDtoOut;

public class SimpleService {
    private final SimpleDtoMapperImpl mapper = new SimpleDtoMapperImpl();

    public SimpleDtoOut process(SimpleDtoIn input) {
        return mapper.toSimpleDtoOut(input);
    }
}
