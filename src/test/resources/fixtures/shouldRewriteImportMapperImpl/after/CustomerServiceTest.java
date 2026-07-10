package io.github.santunioni.fixtures;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(CustomerMapper.class)
public class CustomerServiceTest {
    @Autowired
    private CustomerMapper mapper;
}
