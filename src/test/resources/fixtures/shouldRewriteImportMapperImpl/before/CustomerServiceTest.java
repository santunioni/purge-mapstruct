package io.github.santunioni.fixtures;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(io.github.santunioni.fixtures.CustomerMapperImpl.class)
public class CustomerServiceTest {
    @Autowired
    private CustomerMapperImpl mapper;
}
