package com.santunioni.fixtures.services;

import com.santunioni.fixtures.dtoMappers.SimpleDtoIn;
import com.santunioni.fixtures.dtoMappers.SimpleDtoMapper;
import com.santunioni.fixtures.dtoMappers.SimpleDtoOut;

public class SimpleService {
    private final SimpleDtoMapper mapper = new SimpleDtoMapper();

    public SimpleDtoOut process(SimpleDtoIn input) {
        return mapper.toSimpleDtoOut(input);
    }
}
