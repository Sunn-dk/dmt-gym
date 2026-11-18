package vn.edu.gym.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.gym.model.Payment;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.ReceiptView;
import vn.edu.gym.model.SaleType;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private static final RowMapper<Payment> PAYMENT_ROW_MAPPER = (rs, rowNum) -> new Payment(
            rs.getLong("id"),
            rs.getLong("membership_id"),
            rs.getLong("amount_vnd"),
            rs.getString("method"),
            rs.getTimestamp("paid_at").toLocalDateTime(),
            rs.getLong("collected_by")
    );

    private static final RowMapper<ReceiptView> RECEIPT_ROW_MAPPER = (rs, rowNum) -> {
        LocalDate endDate = rs.getDate("end_date").toLocalDate();
        return new ReceiptView(
                rs.getLong("payment_id"),
                rs.getLong("membership_id"),
                rs.getLong("member_id"),
                rs.getString("member_name"),
                rs.getString("member_phone"),
                rs.getString("plan_name_snapshot"),
                PlanKind.valueOf(rs.getString("kind_snapshot")),
                rs.getInt("duration_days_snapshot"),
                rs.getObject("sessions_total_snapshot", Integer.class),
                rs.getLong("price_vnd_snapshot"),
                SaleType.valueOf(rs.getString("sale_type")),
                rs.getDate("start_date").toLocalDate(),
                endDate,
                endDate.minusDays(1),
                rs.getLong("amount_vnd"),
                rs.getString("method"),
                rs.getTimestamp("paid_at").toLocalDateTime(),
                rs.getLong("collected_by"),
                rs.getString("collected_by_name")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(long membershipId, long amountVnd, LocalDateTime paidAt, long collectedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (membership_id, amount_vnd, method, paid_at, collected_by) "
                            + "VALUES (?, ?, 'CASH', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, membershipId);
            ps.setLong(2, amountVnd);
            ps.setTimestamp(3, Timestamp.valueOf(paidAt));
            ps.setLong(4, collectedBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<Payment> findByMembershipId(long membershipId) {
        List<Payment> list = jdbcTemplate.query(
                "SELECT id, membership_id, amount_vnd, method, paid_at, collected_by "
                        + "FROM payments WHERE membership_id = ?",
                PAYMENT_ROW_MAPPER,
                membershipId
        );
        return list.stream().findFirst();
    }

    public Optional<ReceiptView> findReceiptByPaymentId(long paymentId) {
        List<ReceiptView> list = jdbcTemplate.query(
                receiptSelectSql() + " WHERE p.id = ?",
                RECEIPT_ROW_MAPPER,
                paymentId
        );
        return list.stream().findFirst();
    }

    public Optional<ReceiptView> findReceiptByMembershipId(long membershipId) {
        List<ReceiptView> list = jdbcTemplate.query(
                receiptSelectSql() + " WHERE p.membership_id = ?",
                RECEIPT_ROW_MAPPER,
                membershipId
        );
        return list.stream().findFirst();
    }

    private String receiptSelectSql() {
        return "SELECT p.id AS payment_id, p.membership_id, m.member_id, mb.full_name AS member_name, "
                + "mb.phone AS member_phone, m.plan_name_snapshot, m.kind_snapshot, "
                + "m.duration_days_snapshot, m.sessions_total_snapshot, m.price_vnd_snapshot, "
                + "m.sale_type, m.start_date, m.end_date, p.amount_vnd, p.method, p.paid_at, "
                + "p.collected_by, u.full_name AS collected_by_name "
                + "FROM payments p "
                + "JOIN memberships m ON m.id = p.membership_id "
                + "JOIN members mb ON mb.id = m.member_id "
                + "JOIN users u ON u.id = p.collected_by";
    }
}
