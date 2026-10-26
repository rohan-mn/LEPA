package com.lepa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"lepa.ai-service-url=http://localhost:8000"})
class LepaApplicationTests {
    @Test void contextLoads() {}
}
