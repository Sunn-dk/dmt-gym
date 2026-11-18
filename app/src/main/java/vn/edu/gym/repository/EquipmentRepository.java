package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Equipment;
import vn.edu.gym.model.EquipmentStatus;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EquipmentRepository {

    private static final RowMapper<Equipment> EQUIPMENT_ROW_MAPPER = (rs, rowNum) -> new Equipment(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("location"),
            EquipmentStatus.valueOf(rs.getString("status")),
            rs.getString("note"),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getLong("updated_by"),
            rs.getString("updated_by_name")
    );

    private final JdbcTemplate jdbcTemplate;

    public EquipmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Equipment> findAll(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return jdbcTemplate.query(
                    baseSelectSql() + " ORDER BY e.id ASC",
                    EQUIPMENT_ROW_MAPPER
            );
        }
        String like = SqlLikeUtils.containsPattern(trimmed);
        return jdbcTemplate.query(
                baseSelectSql()
                        + " WHERE (e.code LIKE ? ESCAPE '\\\\' OR e.name LIKE ? ESCAPE '\\\\' OR e.location LIKE ? ESCAPE '\\\\') "
                        + " ORDER BY e.id ASC",
                EQUIPMENT_ROW_MAPPER,
                like,
                like,
                like
        );
    }

    public Optional<Equipment> findById(long id) {
        List<Equipment> list = jdbcTemplate.query(
                baseSelectSql() + " WHERE e.id = ?",
                EQUIPMENT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Equipment> findByIdForUpdate(long id) {
        List<Equipment> list = jdbcTemplate.query(
                "SELECT e.id, e.code, e.name, e.location, e.status, e.note, e.updated_at, e.updated_by, "
                        + "NULL AS updated_by_name FROM equipment e WHERE e.id = ? FOR UPDATE",
                EQUIPMENT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public boolean existsByCode(String code, Long excludeId) {
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM equipment WHERE code = ?",
                    Integer.class,
                    code
            );
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM equipment WHERE code = ? AND id <> ?",
                    Integer.class,
                    code,
                    excludeId
            );
        }
        return count != null && count > 0;
    }

    public long insert(String code, String name, String location, LocalDateTime updatedAt, long updatedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO equipment (code, name, location, status, note, updated_at, updated_by) "
                            + "VALUES (?, ?, ?, 'AVAILABLE', '', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, location);
            ps.setTimestamp(4, Timestamp.valueOf(updatedAt));
            ps.setLong(5, updatedBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateInfo(long id, String code, String name, String location, LocalDateTime updatedAt, long updatedBy) {
        jdbcTemplate.update(
                "UPDATE equipment SET code = ?, name = ?, location = ?, updated_at = ?, updated_by = ? WHERE id = ?",
                code,
                name,
                location,
                Timestamp.valueOf(updatedAt),
                updatedBy,
                id
        );
    }

    public void updateStatus(long id, EquipmentStatus status, String note, LocalDateTime updatedAt, long updatedBy) {
        jdbcTemplate.update(
                "UPDATE equipment SET status = ?, note = ?, updated_at = ?, updated_by = ? WHERE id = ?",
                status.name(),
                note,
                Timestamp.valueOf(updatedAt),
                updatedBy,
                id
        );
    }

    private String baseSelectSql() {
        return "SELECT e.id, e.code, e.name, e.location, e.status, e.note, e.updated_at, e.updated_by, "
                + "u.full_name AS updated_by_name "
                + "FROM equipment e LEFT JOIN users u ON u.id = e.updated_by";
    }
}
