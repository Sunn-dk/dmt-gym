package vn.edu.gym.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides the authoritative server Clock in Asia/Ho_Chi_Minh (BR-01).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId GYM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock gymClock() {
        return Clock.system(GYM_ZONE);
    }
}
