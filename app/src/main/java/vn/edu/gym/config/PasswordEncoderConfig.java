package vn.edu.gym.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing configuration using BCrypt with cost factor 12 (NFR-02 / BR-26).
 */
@Configuration
public class PasswordEncoderConfig {

    public static final int BCRYPT_COST = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_COST);
    }
}
