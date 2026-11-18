package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.Visit;
import vn.edu.gym.model.VisitView;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class VisitRepository {

    private static final RowMapper<Visit> VISIT_ROW_MAPPER = (rs, rowNum) -> {
        Timestamp outTs = rs.getTimestamp("check_out_at");
        return new Visit(
                rs.getLong("id"),
                rs.getLong("member_id"),
                rs.getLong("membership_id"),
                rs.getTimestamp("check_in_at").toLocalDateTime(),
                outTs == null ? null : outTs.toLocalDateTime(),
                rs.getLong("check_in_by"),
                rs.getObject("check_out_by", Long.class)
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public VisitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Visit> findById(long id) {
        List<Visit> list = jdbcTemplate.query(
                "SELECT id, member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by "
                        + "FROM visits WHERE id = ?",
                VISIT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Visit> findByIdForUpdate(long id) {
        List<Visit> list = jdbcTemplate.query(
                "SELECT id, member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by "
                        + "FROM visits WHERE id = ? FOR UPDATE",
                VISIT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Visit> findOpenVisitByMemberForUpdate(long memberId) {
        List<Visit> list = jdbcTemplate.query(
                "SELECT id, member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by "
                        + "FROM visits WHERE member_id = ? AND check_out_at IS NULL FOR UPDATE",
                VISIT_ROW_MAPPER,
                memberId
        );
        return list.stream().findFirst();
    }

    public boolean hasOpenVisit(long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE member_id = ? AND check_out_at IS NULL",
                Integer.class,
                memberId
        );
        return count != null && count > 0;
    }

    public List<VisitView> findOpenVisits(LocalDate today) {
        return jdbcTemplate.query(
                visitViewSelectSql() + " WHERE v.check_out_at IS NULL ORDER BY v.check_in_at ASC, v.id ASC",
                visitViewMapper(today)
        );
    }

    public Optional<VisitView> findViewById(long id, LocalDate today) {
        List<VisitView> list = jdbcTemplate.query(
                visitViewSelectSql() + " WHERE v.id = ?",
                visitViewMapper(today),
                id
        );
        return list.stream().findFirst();
    }

    public PageResult<VisitView> search(
            Long memberId,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate today,
            int page,
            int pageSize
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (memberId != null) {
            where.append(" AND v.member_id = ? ");
            params.add(memberId);
        }
        String trimmed = keyword == null ? "" : keyword.trim();
        if (!trimmed.isEmpty()) {
            String like = SqlLikeUtils.containsPattern(trimmed);
            Long parsedId = tryParseMemberId(trimmed);
            if (parsedId != null) {
                where.append(" AND (m.id = ? OR m.full_name LIKE ? ESCAPE '\\\\' OR m.phone LIKE ? ESCAPE '\\\\') ");
                params.add(parsedId);
                params.add(like);
                params.add(like);
            } else {
                where.append(" AND (m.full_name LIKE ? ESCAPE '\\\\' OR m.phone LIKE ? ESCAPE '\\\\') ");
                params.add(like);
                params.add(like);
            }
        }
        if (fromDate != null) {
            where.append(" AND v.check_in_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            where.append(" AND v.check_in_at < ? ");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits v JOIN members m ON m.id = v.member_id" + where,
                Long.class,
                params.toArray()
        );
        long totalCount = total != null ? total : 0L;
        int safePage = PageResult.clampPage(page, totalCount, pageSize);
        long offset = PageResult.safeOffset(safePage, pageSize);

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(pageSize);
        listParams.add(offset);

        List<VisitView> items = jdbcTemplate.query(
                visitViewSelectSql() + where + " ORDER BY v.check_in_at DESC, v.id DESC LIMIT ? OFFSET ?",
                visitViewMapper(today),
                listParams.toArray()
        );
        return new PageResult<>(items, safePage, pageSize, totalCount);
    }

    public long insertCheckIn(long memberId, long membershipId, LocalDateTime checkInAt, long checkInBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO visits (member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by) "
                            + "VALUES (?, ?, ?, NULL, ?, NULL)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, memberId);
            ps.setLong(2, membershipId);
            ps.setTimestamp(3, Timestamp.valueOf(checkInAt));
            ps.setLong(4, checkInBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int updateCheckOutIfOpen(long visitId, LocalDateTime checkOutAt, long checkOutBy) {
        return jdbcTemplate.update(
                "UPDATE visits SET check_out_at = ?, check_out_by = ? WHERE id = ? AND check_out_at IS NULL",
                Timestamp.valueOf(checkOutAt),
                checkOutBy,
                visitId
        );
    }

    public boolean existsOverlappingVisit(long memberId, LocalDateTime startAt, LocalDateTime endAt) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits "
                        + "WHERE member_id = ? AND check_in_at < ? AND (check_out_at IS NULL OR check_out_at > ?)",
                Integer.class,
                memberId,
                Timestamp.valueOf(endAt),
                Timestamp.valueOf(startAt)
        );
        return count != null && count > 0;
    }

    public List<VisitView> findOverlappingVisits(long memberId, LocalDateTime startAt, LocalDateTime endAt, LocalDate today) {
        return jdbcTemplate.query(
                visitViewSelectSql()
                        + " WHERE v.member_id = ? AND v.check_in_at < ? AND (v.check_out_at IS NULL OR v.check_out_at > ?) "
                        + "ORDER BY v.check_in_at ASC",
                visitViewMapper(today),
                memberId,
                Timestamp.valueOf(endAt),
                Timestamp.valueOf(startAt)
        );
    }

    private String visitViewSelectSql() {
        return "SELECT v.id, v.member_id, m.full_name AS member_name, m.phone AS member_phone, "
                + "v.membership_id, ms.plan_name_snapshot, v.check_in_at, v.check_out_at, "
                + "v.check_in_by, uin.full_name AS check_in_by_name, "
                + "v.check_out_by, uout.full_name AS check_out_by_name "
                + "FROM visits v "
                + "JOIN members m ON m.id = v.member_id "
                + "JOIN memberships ms ON ms.id = v.membership_id "
                + "JOIN users uin ON uin.id = v.check_in_by "
                + "LEFT JOIN users uout ON uout.id = v.check_out_by";
    }

    private RowMapper<VisitView> visitViewMapper(LocalDate today) {
        return (rs, rowNum) -> {
            LocalDateTime checkInAt = rs.getTimestamp("check_in_at").toLocalDateTime();
            Timestamp outTs = rs.getTimestamp("check_out_at");
            LocalDateTime checkOutAt = outTs == null ? null : outTs.toLocalDateTime();
            boolean inside = checkOutAt == null;
            boolean stale = inside && checkInAt.toLocalDate().isBefore(today);
            return new VisitView(
                    rs.getLong("id"),
                    rs.getLong("member_id"),
                    rs.getString("member_name"),
                    rs.getString("member_phone"),
                    rs.getLong("membership_id"),
                    rs.getString("plan_name_snapshot"),
                    checkInAt,
                    checkOutAt,
                    rs.getLong("check_in_by"),
                    rs.getString("check_in_by_name"),
                    rs.getObject("check_out_by", Long.class),
                    rs.getString("check_out_by_name"),
                    inside,
                    stale
            );
        };
    }

    private Long tryParseMemberId(String input) {
        String s = input.toUpperCase();
        if (s.startsWith("HV-")) {
            s = s.substring(3).trim();
        }
        if (s.isEmpty() || !s.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            long val = Long.parseLong(s);
            return val > 0 ? val : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
