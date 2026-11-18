package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Membership;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.SaleType;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MembershipRepository {

    private static final RowMapper<Membership> MEMBERSHIP_ROW_MAPPER = (rs, rowNum) -> new Membership(
            rs.getLong("id"),
            rs.getLong("member_id"),
            rs.getLong("plan_id"),
            SaleType.valueOf(rs.getString("sale_type")),
            rs.getString("request_key"),
            rs.getString("plan_name_snapshot"),
            PlanKind.valueOf(rs.getString("kind_snapshot")),
            rs.getInt("duration_days_snapshot"),
            rs.getObject("sessions_total_snapshot", Integer.class),
            rs.getLong("price_vnd_snapshot"),
            rs.getDate("start_date").toLocalDate(),
            rs.getDate("end_date").toLocalDate(),
            rs.getTimestamp("sold_at").toLocalDateTime(),
            rs.getLong("sold_by")
    );

    private final JdbcTemplate jdbcTemplate;

    public MembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Membership> findById(long id) {
        List<Membership> list = jdbcTemplate.query(
                "SELECT * FROM memberships WHERE id = ?",
                MEMBERSHIP_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Membership> findByIdAndMemberForUpdate(long id, long memberId) {
        List<Membership> list = jdbcTemplate.query(
                "SELECT * FROM memberships WHERE id = ? AND member_id = ? FOR UPDATE",
                MEMBERSHIP_ROW_MAPPER,
                id,
                memberId
        );
        return list.stream().findFirst();
    }

    public Optional<Membership> findByRequestKey(String requestKey) {
        List<Membership> list = jdbcTemplate.query(
                "SELECT * FROM memberships WHERE request_key = ?",
                MEMBERSHIP_ROW_MAPPER,
                requestKey
        );
        return list.stream().findFirst();
    }

    public List<Membership> findByMemberId(long memberId) {
        return jdbcTemplate.query(
                "SELECT * FROM memberships WHERE member_id = ? ORDER BY start_date DESC, id DESC",
                MEMBERSHIP_ROW_MAPPER,
                memberId
        );
    }

    public List<Membership> findPtByMemberId(long memberId) {
        return jdbcTemplate.query(
                "SELECT * FROM memberships WHERE member_id = ? AND kind_snapshot = 'PT' ORDER BY end_date DESC, id DESC",
                MEMBERSHIP_ROW_MAPPER,
                memberId
        );
    }

    public List<Membership> findAllActiveOrFuturePtWithMember() {
        return jdbcTemplate.query(
                "SELECT * FROM memberships WHERE kind_snapshot = 'PT' ORDER BY member_id ASC, end_date DESC, id DESC",
                MEMBERSHIP_ROW_MAPPER
        );
    }

    public long countFutureOrActiveGym(long memberId, LocalDate today) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE member_id = ? AND kind_snapshot = 'GYM' AND end_date > ?",
                Long.class,
                memberId,
                Date.valueOf(today)
        );
        return count != null ? count : 0L;
    }

    public Optional<LocalDate> findMaxGymEndDate(long memberId) {
        Date maxEnd = jdbcTemplate.queryForObject(
                "SELECT MAX(end_date) FROM memberships WHERE member_id = ? AND kind_snapshot = 'GYM'",
                Date.class,
                memberId
        );
        return Optional.ofNullable(maxEnd).map(Date::toLocalDate);
    }

    public Optional<Membership> findActiveGymAtDate(long memberId, LocalDate targetDate) {
        List<Membership> list = jdbcTemplate.query(
                "SELECT * FROM memberships "
                        + "WHERE member_id = ? AND kind_snapshot = 'GYM' AND start_date <= ? AND end_date > ? "
                        + "ORDER BY start_date ASC, id ASC LIMIT 1",
                MEMBERSHIP_ROW_MAPPER,
                memberId,
                Date.valueOf(targetDate),
                Date.valueOf(targetDate)
        );
        return list.stream().findFirst();
    }

    public boolean existsActiveGymAtDate(long memberId, LocalDate targetDate) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memberships "
                        + "WHERE member_id = ? AND kind_snapshot = 'GYM' AND start_date <= ? AND end_date > ?",
                Integer.class,
                memberId,
                Date.valueOf(targetDate),
                Date.valueOf(targetDate)
        );
        return count != null && count > 0;
    }

    public long insert(
            long memberId,
            long planId,
            SaleType saleType,
            String requestKey,
            String planNameSnapshot,
            PlanKind kindSnapshot,
            int durationDaysSnapshot,
            Integer sessionsTotalSnapshot,
            long priceVndSnapshot,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime soldAt,
            long soldBy
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO memberships (member_id, plan_id, sale_type, request_key, plan_name_snapshot, "
                            + "kind_snapshot, duration_days_snapshot, sessions_total_snapshot, price_vnd_snapshot, "
                            + "start_date, end_date, sold_at, sold_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, memberId);
            ps.setLong(2, planId);
            ps.setString(3, saleType.name());
            ps.setString(4, requestKey);
            ps.setString(5, planNameSnapshot);
            ps.setString(6, kindSnapshot.name());
            ps.setInt(7, durationDaysSnapshot);
            if (sessionsTotalSnapshot == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, sessionsTotalSnapshot);
            }
            ps.setLong(9, priceVndSnapshot);
            ps.setDate(10, Date.valueOf(startDate));
            ps.setDate(11, Date.valueOf(endDate));
            ps.setTimestamp(12, Timestamp.valueOf(soldAt));
            ps.setLong(13, soldBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
