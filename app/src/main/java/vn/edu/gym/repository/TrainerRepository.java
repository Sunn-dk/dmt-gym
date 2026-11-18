package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Trainer;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class TrainerRepository {

    private static final RowMapper<Trainer> TRAINER_ROW_MAPPER = (rs, rowNum) -> new Trainer(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("specialty"),
            rs.getBoolean("active")
    );

    private final JdbcTemplate jdbcTemplate;

    public TrainerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Trainer> findAll() {
        return jdbcTemplate.query(
                "SELECT id, full_name, specialty, active FROM trainers ORDER BY active DESC, id ASC",
                TRAINER_ROW_MAPPER
        );
    }

    public List<Trainer> findAllActive() {
        return jdbcTemplate.query(
                "SELECT id, full_name, specialty, active FROM trainers WHERE active = TRUE ORDER BY id ASC",
                TRAINER_ROW_MAPPER
        );
    }

    public List<Trainer> findUnlinkedActiveTrainers() {
        return jdbcTemplate.query(
                "SELECT t.id, t.full_name, t.specialty, t.active "
                        + "FROM trainers t LEFT JOIN users u ON u.trainer_id = t.id "
                        + "WHERE t.active = TRUE AND u.id IS NULL ORDER BY t.id ASC",
                TRAINER_ROW_MAPPER
        );
    }

    public Optional<Trainer> findById(long id) {
        List<Trainer> list = jdbcTemplate.query(
                "SELECT id, full_name, specialty, active FROM trainers WHERE id = ?",
                TRAINER_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Trainer> findByIdForUpdate(long id) {
        List<Trainer> list = jdbcTemplate.query(
                "SELECT id, full_name, specialty, active FROM trainers WHERE id = ? FOR UPDATE",
                TRAINER_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public long insert(String fullName, String specialty) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO trainers (full_name, specialty, active) VALUES (?, ?, TRUE)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, fullName);
            ps.setString(2, specialty);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, String fullName, String specialty) {
        jdbcTemplate.update(
                "UPDATE trainers SET full_name = ?, specialty = ? WHERE id = ?",
                fullName,
                specialty,
                id
        );
    }

    public void updateActive(long id, boolean active) {
        jdbcTemplate.update("UPDATE trainers SET active = ? WHERE id = ?", active, id);
    }
}
