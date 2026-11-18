package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.BookingStatus;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.PtBooking;
import vn.edu.gym.model.PtBookingView;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BookingRepository {

    private static final RowMapper<PtBooking> BOOKING_ROW_MAPPER = (rs, rowNum) -> {
        Timestamp resolvedTs = rs.getTimestamp("resolved_at");
        return new PtBooking(
                rs.getLong("id"),
                rs.getLong("member_id"),
                rs.getLong("membership_id"),
                rs.getLong("trainer_id"),
                rs.getTimestamp("start_at").toLocalDateTime(),
                rs.getTimestamp("end_at").toLocalDateTime(),
                BookingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getLong("created_by"),
                resolvedTs == null ? null : resolvedTs.toLocalDateTime(),
                rs.getObject("resolved_by", Long.class)
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public BookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PtBooking> findById(long id) {
        List<PtBooking> list = jdbcTemplate.query(
                "SELECT * FROM pt_bookings WHERE id = ?",
                BOOKING_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<PtBooking> findByIdForUpdate(long id) {
        List<PtBooking> list = jdbcTemplate.query(
                "SELECT * FROM pt_bookings WHERE id = ? FOR UPDATE",
                BOOKING_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<PtBooking> findExactBookedDuplicate(
            long memberId,
            long membershipId,
            long trainerId,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        List<PtBooking> list = jdbcTemplate.query(
                "SELECT * FROM pt_bookings "
                        + "WHERE member_id = ? AND membership_id = ? AND trainer_id = ? "
                        + "AND start_at = ? AND end_at = ? AND status = 'BOOKED'",
                BOOKING_ROW_MAPPER,
                memberId,
                membershipId,
                trainerId,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt)
        );
        return list.stream().findFirst();
    }

    public List<PtBookingView> findPendingBookedByMember(long memberId, LocalDateTime now) {
        return jdbcTemplate.query(
                bookingViewSelectSql() + " WHERE b.member_id = ? AND b.status = 'BOOKED' ORDER BY b.start_at ASC, b.id ASC",
                bookingViewMapper(now),
                memberId
        );
    }

    public List<PtBookingView> findPendingBookedByTrainer(long trainerId, LocalDateTime now) {
        return jdbcTemplate.query(
                bookingViewSelectSql() + " WHERE b.trainer_id = ? AND b.status = 'BOOKED' ORDER BY b.start_at ASC, b.id ASC",
                bookingViewMapper(now),
                trainerId
        );
    }

    public List<PtBookingView> findBookedOverlappingRangeForTrainer(
            long trainerId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime now
    ) {
        return jdbcTemplate.query(
                bookingViewSelectSql()
                        + " WHERE b.trainer_id = ? AND b.status = 'BOOKED' AND b.start_at < ? AND b.end_at > ? "
                        + "ORDER BY b.start_at ASC, b.id ASC",
                bookingViewMapper(now),
                trainerId,
                Timestamp.valueOf(endAt),
                Timestamp.valueOf(startAt)
        );
    }

    public List<PtBooking> findTrainerConflicts(long trainerId, LocalDateTime startAt, LocalDateTime endAt) {
        return jdbcTemplate.query(
                "SELECT * FROM pt_bookings WHERE trainer_id = ? AND status = 'BOOKED' AND start_at < ? AND end_at > ?",
                BOOKING_ROW_MAPPER,
                trainerId,
                Timestamp.valueOf(endAt),
                Timestamp.valueOf(startAt)
        );
    }

    public List<PtBooking> findMemberConflicts(long memberId, LocalDateTime startAt, LocalDateTime endAt) {
        return jdbcTemplate.query(
                "SELECT * FROM pt_bookings WHERE member_id = ? AND status = 'BOOKED' AND start_at < ? AND end_at > ?",
                BOOKING_ROW_MAPPER,
                memberId,
                Timestamp.valueOf(endAt),
                Timestamp.valueOf(startAt)
        );
    }

    public int countUsedByMembership(long membershipId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pt_bookings WHERE membership_id = ? AND status = 'COMPLETED'",
                Integer.class,
                membershipId
        );
        return count != null ? count : 0;
    }

    public int countReservedByMembership(long membershipId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pt_bookings WHERE membership_id = ? AND status = 'BOOKED'",
                Integer.class,
                membershipId
        );
        return count != null ? count : 0;
    }

    public Optional<PtBookingView> findViewById(long id, LocalDateTime now) {
        List<PtBookingView> list = jdbcTemplate.query(
                bookingViewSelectSql() + " WHERE b.id = ?",
                bookingViewMapper(now),
                id
        );
        return list.stream().findFirst();
    }

    public List<PtBookingView> findOverduePending(Long trainerId, LocalDateTime now) {
        StringBuilder sql = new StringBuilder(
                bookingViewSelectSql() + " WHERE b.status = 'BOOKED' AND b.end_at <= ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(now));
        if (trainerId != null) {
            sql.append(" AND b.trainer_id = ? ");
            params.add(trainerId);
        }
        sql.append(" ORDER BY b.start_at ASC, b.id ASC");
        return jdbcTemplate.query(sql.toString(), bookingViewMapper(now), params.toArray());
    }

    public PageResult<PtBookingView> search(
            LocalDate date,
            Long trainerId,
            Long memberId,
            String statusFilter,
            LocalDateTime now,
            int page,
            int pageSize
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            where.append(" AND b.start_at >= ? AND b.start_at < ? ");
            params.add(Timestamp.valueOf(date.atStartOfDay()));
            params.add(Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        }
        if (trainerId != null) {
            where.append(" AND b.trainer_id = ? ");
            params.add(trainerId);
        }
        if (memberId != null) {
            where.append(" AND b.member_id = ? ");
            params.add(memberId);
        }
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            if ("OVERDUE_PENDING".equalsIgnoreCase(statusFilter)) {
                where.append(" AND b.status = 'BOOKED' AND b.end_at <= ? ");
                params.add(Timestamp.valueOf(now));
            } else {
                where.append(" AND b.status = ? ");
                params.add(BookingStatus.valueOf(statusFilter.trim().toUpperCase()).name());
            }
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pt_bookings b" + where,
                Long.class,
                params.toArray()
        );
        long totalCount = total != null ? total : 0L;
        int safePage = PageResult.clampPage(page, totalCount, pageSize);
        long offset = PageResult.safeOffset(safePage, pageSize);

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(pageSize);
        listParams.add(offset);

        List<PtBookingView> items = jdbcTemplate.query(
                bookingViewSelectSql() + where + " ORDER BY b.start_at DESC, b.id DESC LIMIT ? OFFSET ?",
                bookingViewMapper(now),
                listParams.toArray()
        );
        return new PageResult<>(items, safePage, pageSize, totalCount);
    }

    public long insertBooked(
            long memberId,
            long membershipId,
            long trainerId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime createdAt,
            long createdBy
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pt_bookings "
                            + "(member_id, membership_id, trainer_id, start_at, end_at, status, created_at, created_by, resolved_at, resolved_by) "
                            + "VALUES (?, ?, ?, ?, ?, 'BOOKED', ?, ?, NULL, NULL)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, memberId);
            ps.setLong(2, membershipId);
            ps.setLong(3, trainerId);
            ps.setTimestamp(4, Timestamp.valueOf(startAt));
            ps.setTimestamp(5, Timestamp.valueOf(endAt));
            ps.setTimestamp(6, Timestamp.valueOf(createdAt));
            ps.setLong(7, createdBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int resolveIfBooked(long id, BookingStatus targetStatus, LocalDateTime resolvedAt, long resolvedBy) {
        return jdbcTemplate.update(
                "UPDATE pt_bookings SET status = ?, resolved_at = ?, resolved_by = ? WHERE id = ? AND status = 'BOOKED'",
                targetStatus.name(),
                Timestamp.valueOf(resolvedAt),
                resolvedBy,
                id
        );
    }

    private String bookingViewSelectSql() {
        return "SELECT b.id, b.member_id, m.full_name AS member_name, b.membership_id, "
                + "ms.plan_name_snapshot, b.trainer_id, t.full_name AS trainer_name, "
                + "b.start_at, b.end_at, b.status, b.created_at, b.created_by, uc.full_name AS created_by_name, "
                + "b.resolved_at, b.resolved_by, ur.full_name AS resolved_by_name "
                + "FROM pt_bookings b "
                + "JOIN members m ON m.id = b.member_id "
                + "JOIN memberships ms ON ms.id = b.membership_id "
                + "JOIN trainers t ON t.id = b.trainer_id "
                + "JOIN users uc ON uc.id = b.created_by "
                + "LEFT JOIN users ur ON ur.id = b.resolved_by";
    }

    private RowMapper<PtBookingView> bookingViewMapper(LocalDateTime now) {
        return (rs, rowNum) -> {
            LocalDateTime startAt = rs.getTimestamp("start_at").toLocalDateTime();
            LocalDateTime endAt = rs.getTimestamp("end_at").toLocalDateTime();
            BookingStatus status = BookingStatus.valueOf(rs.getString("status"));
            boolean canResolveNow = status == BookingStatus.BOOKED && !now.isBefore(endAt);
            Timestamp resolvedTs = rs.getTimestamp("resolved_at");
            return new PtBookingView(
                    rs.getLong("id"),
                    rs.getLong("member_id"),
                    rs.getString("member_name"),
                    rs.getLong("membership_id"),
                    rs.getString("plan_name_snapshot"),
                    rs.getLong("trainer_id"),
                    rs.getString("trainer_name"),
                    startAt,
                    endAt,
                    status,
                    canResolveNow,
                    canResolveNow,
                    false,
                    "",
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getLong("created_by"),
                    rs.getString("created_by_name"),
                    resolvedTs == null ? null : resolvedTs.toLocalDateTime(),
                    rs.getObject("resolved_by", Long.class),
                    rs.getString("resolved_by_name")
            );
        };
    }
}
