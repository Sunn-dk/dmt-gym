package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Plan;
import vn.edu.gym.model.PlanKind;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class PlanRepository {

    private static final RowMapper<Plan> PLAN_ROW_MAPPER = (rs, rowNum) -> new Plan(
            rs.getLong("id"),
            rs.getString("name"),
            PlanKind.valueOf(rs.getString("kind")),
            rs.getInt("duration_days"),
            rs.getObject("sessions_total", Integer.class),
            rs.getLong("price_vnd"),
            rs.getBoolean("active")
    );

    private final JdbcTemplate jdbcTemplate;

    public PlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Plan> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, kind, duration_days, sessions_total, price_vnd, active "
                        + "FROM plans ORDER BY active DESC, kind ASC, duration_days ASC, id ASC",
                PLAN_ROW_MAPPER
        );
    }

    public List<Plan> findAllActive() {
        return jdbcTemplate.query(
                "SELECT id, name, kind, duration_days, sessions_total, price_vnd, active "
                        + "FROM plans WHERE active = TRUE ORDER BY kind ASC, duration_days ASC, id ASC",
                PLAN_ROW_MAPPER
        );
    }

    public List<Plan> findActiveByKind(PlanKind kind) {
        return jdbcTemplate.query(
                "SELECT id, name, kind, duration_days, sessions_total, price_vnd, active "
                        + "FROM plans WHERE active = TRUE AND kind = ? ORDER BY duration_days ASC, id ASC",
                PLAN_ROW_MAPPER,
                kind.name()
        );
    }

    public Optional<Plan> findById(long id) {
        List<Plan> list = jdbcTemplate.query(
                "SELECT id, name, kind, duration_days, sessions_total, price_vnd, active FROM plans WHERE id = ?",
                PLAN_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Plan> findByIdForUpdate(long id) {
        List<Plan> list = jdbcTemplate.query(
                "SELECT id, name, kind, duration_days, sessions_total, price_vnd, active FROM plans WHERE id = ? FOR UPDATE",
                PLAN_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public long insert(String name, PlanKind kind, int durationDays, Integer sessionsTotal, long priceVnd) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO plans (name, kind, duration_days, sessions_total, price_vnd, active) "
                            + "VALUES (?, ?, ?, ?, ?, TRUE)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, kind.name());
            ps.setInt(3, durationDays);
            if (sessionsTotal == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, sessionsTotal);
            }
            ps.setLong(5, priceVnd);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, String name, PlanKind kind, int durationDays, Integer sessionsTotal, long priceVnd) {
        jdbcTemplate.update(
                "UPDATE plans SET name = ?, kind = ?, duration_days = ?, sessions_total = ?, price_vnd = ? WHERE id = ?",
                name,
                kind.name(),
                durationDays,
                sessionsTotal,
                priceVnd,
                id
        );
    }

    public void updateActive(long id, boolean active) {
        jdbcTemplate.update("UPDATE plans SET active = ? WHERE id = ?", active, id);
    }
}
