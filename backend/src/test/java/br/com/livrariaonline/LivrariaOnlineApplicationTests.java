package br.com.livrariaonline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class LivrariaOnlineApplicationTests {
    @Test
    void contextLoads() {
    }
}
