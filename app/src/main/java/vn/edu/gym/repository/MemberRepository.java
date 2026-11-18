package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Member;
import vn.edu.gym.model.PageResult;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MemberRepository {

    private static final RowMapper<Member> MEMBER_VIEW_MAPPER = (rs, rowNum) -> new Member(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("phone"),
            rs.getDate("birth_date").toLocalDate(),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getLong("created_by"),
            rs.getString("created_by_name")
    );

    private static final RowMapper<Member> MEMBER_BASE_MAPPER = (rs, rowNum) -> new Member(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("phone"),
            rs.getDate("birth_date").toLocalDate(),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getLong("created_by"),
            null
    );

    private final JdbcTemplate jdbcTemplate;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<Member> search(String keyword, int page, int pageSize) {
        String trimmed = keyword == null ? "" : keyword.trim();
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (!trimmed.isEmpty()) {
            String likePattern = SqlLikeUtils.containsPattern(trimmed);
            Long parsedId = tryParseMemberId(trimmed);
            if (parsedId != null) {
                where.append(" WHERE (m.id = ? OR m.full_name LIKE ? ESCAPE '\\\\' OR m.phone LIKE ? ESCAPE '\\\\') ");
                params.add(parsedId);
                params.add(likePattern);
                params.add(likePattern);
            } else {
                where.append(" WHERE (m.full_name LIKE ? ESCAPE '\\\\' OR m.phone LIKE ? ESCAPE '\\\\') ");
                params.add(likePattern);
                params.add(likePattern);
            }
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members m" + where,
                Long.class,
                params.toArray()
        );
        long totalCount = total != null ? total : 0L;
        int safePage = PageResult.clampPage(page, totalCount, pageSize);
        long offset = PageResult.safeOffset(safePage, pageSize);

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(pageSize);
        listParams.add(offset);

        List<Member> items = jdbcTemplate.query(
                "SELECT m.id, m.full_name, m.phone, m.birth_date, m.active, m.created_at, m.created_by, "
                        + "u.full_name AS created_by_name "
                        + "FROM members m LEFT JOIN users u ON u.id = m.created_by "
                        + where
                        + "ORDER BY m.id DESC LIMIT ? OFFSET ?",
                MEMBER_VIEW_MAPPER,
                listParams.toArray()
        );
        return new PageResult<>(items, safePage, pageSize, totalCount);
    }

    public List<Member> findAllActiveForSelect() {
        return jdbcTemplate.query(
                "SELECT m.id, m.full_name, m.phone, m.birth_date, m.active, m.created_at, m.created_by, "
                        + "NULL AS created_by_name "
                        + "FROM members m WHERE m.active = TRUE ORDER BY m.id ASC",
                MEMBER_VIEW_MAPPER
        );
    }

    public List<Member> findAllBasicForSelect() {
        return jdbcTemplate.query(
                "SELECT m.id, m.full_name, m.phone, m.birth_date, m.active, m.created_at, m.created_by, "
                        + "NULL AS created_by_name "
                        + "FROM members m ORDER BY m.id ASC",
                MEMBER_VIEW_MAPPER
        );
    }

    public Optional<Member> findById(long id) {
        List<Member> list = jdbcTemplate.query(
                "SELECT m.id, m.full_name, m.phone, m.birth_date, m.active, m.created_at, m.created_by, "
                        + "u.full_name AS created_by_name "
                        + "FROM members m LEFT JOIN users u ON u.id = m.created_by "
                        + "WHERE m.id = ?",
                MEMBER_VIEW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Member> findByIdForUpdate(long id) {
        List<Member> list = jdbcTemplate.query(
                "SELECT id, full_name, phone, birth_date, active, created_at, created_by "
                        + "FROM members WHERE id = ? FOR UPDATE",
                MEMBER_BASE_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public boolean existsByPhone(String phone, Long excludeId) {
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE phone = ?",
                    Integer.class,
                    phone
            );
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE phone = ? AND id <> ?",
                    Integer.class,
                    phone,
                    excludeId
            );
        }
        return count != null && count > 0;
    }

    public long insert(String fullName, String phone, LocalDate birthDate, LocalDateTime createdAt, long createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO members (full_name, phone, birth_date, active, created_at, created_by) "
                            + "VALUES (?, ?, ?, TRUE, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setDate(3, Date.valueOf(birthDate));
            ps.setTimestamp(4, Timestamp.valueOf(createdAt));
            ps.setLong(5, createdBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, String fullName, String phone, LocalDate birthDate) {
        jdbcTemplate.update(
                "UPDATE members SET full_name = ?, phone = ?, birth_date = ? WHERE id = ?",
                fullName,
                phone,
                Date.valueOf(birthDate),
                id
        );
    }

    public void updateActive(long id, boolean active) {
        jdbcTemplate.update("UPDATE members SET active = ? WHERE id = ?", active, id);
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
