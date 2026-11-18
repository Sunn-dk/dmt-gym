package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.ExpiringMemberView;
import vn.edu.gym.model.ReportSummary;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReportSummary summarize(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime startDt = fromDate.atStartOfDay();
        LocalDateTime endExclusiveDt = toDate.plusDays(1).atStartOfDay();
        Timestamp startTs = Timestamp.valueOf(startDt);
        Timestamp endTs = Timestamp.valueOf(endExclusiveDt);

        Long revenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_vnd), 0) FROM payments WHERE paid_at >= ? AND paid_at < ?",
                Long.class,
                startTs,
                endTs
        );
        Long visits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE check_in_at >= ? AND check_in_at < ?",
                Long.class,
                startTs,
                endTs
        );
        Long completedPt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pt_bookings WHERE status = 'COMPLETED' AND end_at >= ? AND end_at < ?",
                Long.class,
                startTs,
                endTs
        );

        return new ReportSummary(
                fromDate,
                toDate,
                revenue != null ? revenue : 0L,
                visits != null ? visits : 0L,
                completedPt != null ? completedPt : 0L
        );
    }

    public List<ExpiringMemberView> findExpiringGymMembers(LocalDate today, int windowDays) {
        LocalDate windowEnd = today.plusDays(windowDays);
        return jdbcTemplate.query(
                "SELECT m.id AS member_id, m.full_name, m.phone, MAX(ms.end_date) AS coverage_end "
                        + "FROM members m "
                        + "JOIN memberships ms ON ms.member_id = m.id AND ms.kind_snapshot = 'GYM' "
                        + "WHERE m.active = TRUE "
                        + "  AND EXISTS ( "
                        + "      SELECT 1 FROM memberships cur "
                        + "      WHERE cur.member_id = m.id "
                        + "        AND cur.kind_snapshot = 'GYM' "
                        + "        AND cur.start_date <= ? "
                        + "        AND cur.end_date > ? "
                        + "  ) "
                        + "GROUP BY m.id, m.full_name, m.phone "
                        + "HAVING DATE_SUB(MAX(ms.end_date), INTERVAL 1 DAY) BETWEEN ? AND ? "
                        + "ORDER BY coverage_end ASC, m.id ASC",
                (rs, rowNum) -> {
                    LocalDate coverageEnd = rs.getDate("coverage_end").toLocalDate();
                    LocalDate lastUsable = coverageEnd.minusDays(1);
                    long daysRemaining = ChronoUnit.DAYS.between(today, lastUsable);
                    return new ExpiringMemberView(
                            rs.getLong("member_id"),
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            coverageEnd,
                            lastUsable,
                            daysRemaining
                    );
                },
                Date.valueOf(today),
                Date.valueOf(today),
                Date.valueOf(today),
                Date.valueOf(windowEnd)
        );
    }
}
