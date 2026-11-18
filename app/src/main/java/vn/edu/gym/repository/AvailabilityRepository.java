package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.AvailabilitySlot;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AvailabilityRepository {

    private static final RowMapper<AvailabilitySlot> SLOT_ROW_MAPPER = (rs, rowNum) -> new AvailabilitySlot(
            rs.getLong("id"),
            rs.getLong("trainer_id"),
            rs.getString("trainer_name"),
            rs.getTimestamp("start_at").toLocalDateTime(),
            rs.getTimestamp("end_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public AvailabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AvailabilitySlot> findById(long id) {
        List<AvailabilitySlot> list = jdbcTemplate.query(
                "SELECT a.id, a.trainer_id, t.full_name AS trainer_name, a.start_at, a.end_at "
                        + "FROM trainer_availability a JOIN trainers t ON t.id = a.trainer_id "
                        + "WHERE a.id = ?",
                SLOT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<AvailabilitySlot> findByIdForUpdate(long id) {
        List<AvailabilitySlot> list = jdbcTemplate.query(
                "SELECT a.id, a.trainer_id, t.full_name AS trainer_name, a.start_at, a.end_at "
                        + "FROM trainer_availability a JOIN trainers t ON t.id = a.trainer_id "
                        + "WHERE a.id = ? FOR UPDATE",
                SLOT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public List<AvailabilitySlot> findFiltered(Long trainerId, LocalDate date) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.trainer_id, t.full_name AS trainer_name, a.start_at, a.end_at "
                        + "FROM trainer_availability a JOIN trainers t ON t.id = a.trainer_id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        if (trainerId != null) {
            sql.append(" AND a.trainer_id = ? ");
            params.add(trainerId);
        }
        if (date != null) {
            sql.append(" AND a.start_at >= ? AND a.start_at < ? ");
            params.add(Timestamp.valueOf(date.atStartOfDay()));
            params.add(Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY a.start_at ASC, a.trainer_id ASC, a.id ASC");
        return jdbcTemplate.query(sql.toString(), SLOT_ROW_MAPPER, params.toArray());
    }

    public boolean existsOverlapping(long trainerId, LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM trainer_availability "
                            + "WHERE trainer_id = ? AND start_at < ? AND end_at > ?",
                    Integer.class,
                    trainerId,
                    Timestamp.valueOf(endAt),
                    Timestamp.valueOf(startAt)
            );
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM trainer_availability "
                            + "WHERE trainer_id = ? AND id <> ? AND start_at < ? AND end_at > ?",
                    Integer.class,
                    trainerId,
                    excludeId,
                    Timestamp.valueOf(endAt),
                    Timestamp.valueOf(startAt)
            );
        }
        return count != null && count > 0;
    }

    public boolean existsContainingRange(long trainerId, LocalDateTime startAt, LocalDateTime endAt) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trainer_availability "
                        + "WHERE trainer_id = ? AND start_at <= ? AND end_at >= ?",
                Integer.class,
                trainerId,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt)
        );
        return count != null && count > 0;
    }

    public long insert(long trainerId, LocalDateTime startAt, LocalDateTime endAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO trainer_availability (trainer_id, start_at, end_at) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, trainerId);
            ps.setTimestamp(2, Timestamp.valueOf(startAt));
            ps.setTimestamp(3, Timestamp.valueOf(endAt));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, LocalDateTime startAt, LocalDateTime endAt) {
        jdbcTemplate.update(
                "UPDATE trainer_availability SET start_at = ?, end_at = ? WHERE id = ?",
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt),
                id
        );
    }

    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM trainer_availability WHERE id = ?", id);
    }
}
