package vn.edu.gym;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;

@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(LocalDateTime.of(2026, 11, 17, 10, 0));
    }
}
