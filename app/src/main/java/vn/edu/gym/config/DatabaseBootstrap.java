package vn.edu.gym.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Non-destructive database initializer for Milestone A0.
 * Never drops tables or overwrites existing business data on application restart.
 */
@Component
public class DatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean bootstrapEnabled;
    private final String demoPassword;

    public DatabaseBootstrap(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${gym.bootstrap.enabled:true}") boolean bootstrapEnabled,
            @Value("${gym.demo.password:Gym@12345}") String demoPassword
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEnabled = bootstrapEnabled;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            return;
        }
        if (demoPassword == null || demoPassword.getBytes(StandardCharsets.UTF_8).length < 8
                || demoPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("GYM_DEMO_PASSWORD phải dài từ 8 đến 72 byte UTF-8.");
        }

        if (!tableExists("users")) {
            log.info("Database appears empty; running schema.sql...");
            runScript("db/schema.sql");
        }

        long trainerCount = queryCount("SELECT COUNT(*) FROM trainers");
        long userCount = queryCount("SELECT COUNT(*) FROM users");

        if (trainerCount == 0 && userCount == 0) {
            log.info("Seeding sample trainers...");
            runScript("db/seed_trainers.sql");
        }

        if (userCount == 0) {
            log.info("Bootstrapping 4 fixed-ID demo users with BCrypt cost 12...");
            bootstrapUsers();
        }

        long planCount = queryCount("SELECT COUNT(*) FROM plans");
        long memberCount = queryCount("SELECT COUNT(*) FROM members");
        long membershipCount = queryCount("SELECT COUNT(*) FROM memberships");

        if (planCount == 0 && memberCount == 0 && membershipCount == 0) {
            log.info("Seeding initial demo data via seed.sql...");
            runScript("db/seed.sql");
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private long queryCount(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    private void bootstrapUsers() {
        String sql = "INSERT INTO users (id, username, password_hash, full_name, role, trainer_id, credential_version, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0, TRUE)";
        String hash1 = passwordEncoder.encode(demoPassword);
        String hash2 = passwordEncoder.encode(demoPassword);
        String hash3 = passwordEncoder.encode(demoPassword);
        String hash4 = passwordEncoder.encode(demoPassword);

        jdbcTemplate.update(sql, 1L, "manager", hash1, "Quản lý mẫu", "MANAGER", null);
        jdbcTemplate.update(sql, 2L, "reception", hash2, "Lễ tân mẫu", "RECEPTIONIST", null);
        jdbcTemplate.update(sql, 3L, "ptminh", hash3, "PT mẫu Minh", "TRAINER", 1L);
        jdbcTemplate.update(sql, 4L, "ptnam", hash4, "PT mẫu Nam", "TRAINER", 2L);
    }

    private void runScript(String classpathResource) {
        try (Connection conn = dataSource.getConnection()) {
            try {
                ScriptUtils.executeSqlScript(conn, new ClassPathResource(classpathResource));
            } catch (RuntimeException ex) {
                try {
                    if (!conn.getAutoCommit()) {
                        conn.rollback();
                    }
                } catch (SQLException ignored) {
                    // Best-effort rollback on failed script
                }
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể thực thi SQL script: " + classpathResource, e);
        }
    }
}
