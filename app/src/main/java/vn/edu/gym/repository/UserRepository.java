package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.UserAccount;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<UserAccount> USER_ROW_MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            rs.getObject("trainer_id", Long.class),
            rs.getInt("credential_version"),
            rs.getBoolean("active"),
            rs.getString("trainer_name")
    );

    private static final RowMapper<UserAccount> BASE_USER_ROW_MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            rs.getObject("trainer_id", Long.class),
            rs.getInt("credential_version"),
            rs.getBoolean("active"),
            null
    );

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> list = jdbcTemplate.query(
                "SELECT u.id, u.username, u.password_hash, u.full_name, u.role, u.trainer_id, "
                        + "u.credential_version, u.active, t.full_name AS trainer_name "
                        + "FROM users u LEFT JOIN trainers t ON t.id = u.trainer_id "
                        + "WHERE u.username = ?",
                USER_ROW_MAPPER,
                username
        );
        return list.stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        List<UserAccount> list = jdbcTemplate.query(
                "SELECT u.id, u.username, u.password_hash, u.full_name, u.role, u.trainer_id, "
                        + "u.credential_version, u.active, t.full_name AS trainer_name "
                        + "FROM users u LEFT JOIN trainers t ON t.id = u.trainer_id "
                        + "WHERE u.id = ?",
                USER_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public List<UserAccount> findAll() {
        return jdbcTemplate.query(
                "SELECT u.id, u.username, u.password_hash, u.full_name, u.role, u.trainer_id, "
                        + "u.credential_version, u.active, t.full_name AS trainer_name "
                        + "FROM users u LEFT JOIN trainers t ON t.id = u.trainer_id "
                        + "ORDER BY u.id ASC",
                USER_ROW_MAPPER
        );
    }

    public List<UserAccount> findManagersForUpdate() {
        return jdbcTemplate.query(
                "SELECT id, username, password_hash, full_name, role, trainer_id, credential_version, active "
                        + "FROM users WHERE role = 'MANAGER' ORDER BY id ASC FOR UPDATE",
                BASE_USER_ROW_MAPPER
        );
    }

    public Optional<UserAccount> findByIdForUpdate(long id) {
        List<UserAccount> list = jdbcTemplate.query(
                "SELECT id, username, password_hash, full_name, role, trainer_id, credential_version, active "
                        + "FROM users WHERE id = ? FOR UPDATE",
                BASE_USER_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    public boolean existsByTrainerId(long trainerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE trainer_id = ?",
                Integer.class,
                trainerId
        );
        return count != null && count > 0;
    }

    public long insert(String username, String passwordHash, String fullName, Role role, Long trainerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (username, password_hash, full_name, role, trainer_id, credential_version, active) "
                            + "VALUES (?, ?, ?, ?, ?, 0, TRUE)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, role.name());
            if (trainerId == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, trainerId);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateFullName(long id, String fullName) {
        jdbcTemplate.update("UPDATE users SET full_name = ? WHERE id = ?", fullName, id);
    }

    public void updateActiveAndBumpVersion(long id, boolean active) {
        jdbcTemplate.update(
                "UPDATE users SET active = ?, credential_version = credential_version + 1 WHERE id = ?",
                active,
                id
        );
    }

    public void updatePasswordAndBumpVersion(long id, String passwordHash) {
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, credential_version = credential_version + 1 WHERE id = ?",
                passwordHash,
                id
        );
    }
}
