package vn.edu.gym.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Member;
import vn.edu.gym.model.Membership;
import vn.edu.gym.model.Payment;
import vn.edu.gym.model.Plan;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.ReceiptView;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.SaleCommand;
import vn.edu.gym.model.SaleQuote;
import vn.edu.gym.model.SaleType;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.repository.MembershipRepository;
import vn.edu.gym.repository.PaymentRepository;
import vn.edu.gym.repository.PlanRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SaleService {

    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate readCommittedTx;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;

    public SaleService(
            MemberRepository memberRepository,
            PlanRepository planRepository,
            MembershipRepository membershipRepository,
            PaymentRepository paymentRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;

        this.readCommittedTx = new TransactionTemplate(transactionManager);
        this.readCommittedTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTx.setReadOnly(true);
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public SaleQuote prepareQuote(long memberId, long planId, SaleType saleType, String requestKey) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên."));
        if (!member.active()) {
            throw new GymException(ErrorCode.MEMBER_INACTIVE,
                    "Hội viên " + member.memberCode() + " đang ngừng phục vụ, không thể mua hoặc gia hạn gói.");
        }
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy gói tập."));
        if (!plan.active()) {
            throw new GymException(ErrorCode.PLAN_INACTIVE,
                    "Gói \"" + plan.name() + "\" đã ngừng bán.");
        }
        if (saleType == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Loại giao dịch phải là PURCHASE hoặc RENEWAL.");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = computeExpectedStart(memberId, plan, saleType, today);
        LocalDate endDate = startDate.plusDays(plan.durationDays());
        String effectiveKey = isValidUuid(requestKey) ? requestKey : UUID.randomUUID().toString();

        return new SaleQuote(
                member.id(),
                member.fullName(),
                plan.id(),
                plan.name(),
                saleType,
                plan.kind(),
                plan.durationDays(),
                plan.sessionsTotal(),
                plan.priceVnd(),
                startDate,
                endDate,
                endDate.minusDays(1),
                effectiveKey
        );
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public ReceiptView sell(SaleCommand cmd) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST);
        validateCommandBasics(cmd);

        try {
            return readCommittedTx.execute(status -> sellInTransaction(cmd, actor));
        } catch (DuplicateKeyException ex) {
            // Unique request_key conflict: inner tx rolled back; look up committed record in a fresh tx
            return readOnlyTx.execute(status -> resolveAfterUniqueConflict(cmd, ex));
        }
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public ReceiptView getReceipt(long paymentId) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        return paymentRepository.findReceiptByPaymentId(paymentId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu thu PT-" + paymentId + "."));
    }

    private ReceiptView sellInTransaction(SaleCommand cmd, CurrentUser actor) {
        Member member = memberRepository.findByIdForUpdate(cmd.getMemberId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên."));

        Optional<Membership> existingOpt = membershipRepository.findByRequestKey(cmd.getRequestKey());
        if (existingOpt.isPresent()) {
            return verifyMatchingExistingSale(existingOpt.get(), cmd);
        }

        if (!member.active()) {
            throw new GymException(ErrorCode.MEMBER_INACTIVE,
                    "Hội viên " + member.memberCode() + " đang ngừng phục vụ, không thể giao dịch.");
        }

        Plan plan = planRepository.findByIdForUpdate(cmd.getPlanId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy gói tập."));
        if (!plan.active()) {
            throw new GymException(ErrorCode.PLAN_INACTIVE, "Gói \"" + plan.name() + "\" đã ngừng bán.");
        }

        if (plan.kind() != cmd.getQuotedKind()
                || plan.durationDays() != cmd.getQuotedDurationDays()
                || !Objects.equals(plan.sessionsTotal(), cmd.getQuotedSessionsTotal())
                || plan.priceVnd() != cmd.getQuotedPriceVnd()) {
            throw new GymException(ErrorCode.PRICE_CHANGED,
                    "Thông tin giá, loại hoặc thời hạn của gói \"" + plan.name()
                            + "\" vừa thay đổi. Vui lòng xem lại báo giá mới trước khi xác nhận thu tiền.");
        }

        if (!cmd.isCashConfirmed()) {
            throw new GymException(ErrorCode.AMOUNT_MISMATCH,
                    "Vui lòng đánh dấu xác nhận đã nhận đủ tiền mặt trước khi lưu.");
        }
        if (cmd.getAmountReceivedVnd() == null || cmd.getAmountReceivedVnd() != plan.priceVnd()) {
            throw new GymException(ErrorCode.AMOUNT_MISMATCH,
                    "Số tiền thu (" + (cmd.getAmountReceivedVnd() == null ? "trống" : cmd.getAmountReceivedVnd() + "đ")
                            + ") phải khớp chính xác giá gói (" + plan.priceVnd() + "đ).");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = computeExpectedStart(member.id(), plan, cmd.getSaleType(), today);
        LocalDate endDate = startDate.plusDays(plan.durationDays());

        if (!startDate.equals(cmd.getExpectedStart()) || !endDate.equals(cmd.getExpectedEnd())) {
            throw new GymException(ErrorCode.COVERAGE_CHANGED,
                    "Khoảng hiệu lực GYM đã thay đổi thành " + startDate + " đến " + endDate.minusDays(1)
                            + ". Vui lòng xem và xác nhận lại báo giá mới.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long membershipId = membershipRepository.insert(
                member.id(),
                plan.id(),
                cmd.getSaleType(),
                cmd.getRequestKey(),
                plan.name(),
                plan.kind(),
                plan.durationDays(),
                plan.sessionsTotal(),
                plan.priceVnd(),
                startDate,
                endDate,
                now,
                actor.id()
        );
        long paymentId = paymentRepository.insert(membershipId, plan.priceVnd(), now, actor.id());

        return paymentRepository.findReceiptByPaymentId(paymentId)
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    private LocalDate computeExpectedStart(long memberId, Plan plan, SaleType saleType, LocalDate today) {
        if (saleType == SaleType.PURCHASE) {
            if (plan.kind() == PlanKind.GYM) {
                if (membershipRepository.countFutureOrActiveGym(memberId, today) > 0) {
                    throw new GymException(ErrorCode.ALREADY_COVERED,
                            "Hội viên đã có hợp đồng GYM đang hiệu lực hoặc sắp hiệu lực; vui lòng chuyển sang Gia hạn GYM.");
                }
            }
            return today;
        }
        // RENEWAL
        if (plan.kind() != PlanKind.GYM) {
            throw new GymException(ErrorCode.INVALID_INPUT,
                    "Chỉ hỗ trợ gia hạn nối ngày đối với gói GYM; gói PT được mua thành hợp đồng buổi riêng.");
        }
        LocalDate maxEnd = membershipRepository.findMaxGymEndDate(memberId)
                .orElseThrow(() -> new GymException(ErrorCode.NO_MEMBERSHIP,
                        "Hội viên chưa từng có hợp đồng GYM trước đây; vui lòng chọn Mua gói mới."));
        return maxEnd.isAfter(today) ? maxEnd : today;
    }

    private ReceiptView resolveAfterUniqueConflict(SaleCommand cmd, DuplicateKeyException ex) {
        Optional<Membership> existingOpt = membershipRepository.findByRequestKey(cmd.getRequestKey());
        if (existingOpt.isEmpty()) {
            throw new GymException(ErrorCode.STORAGE_ERROR, "Xung đột dữ liệu giao dịch; vui lòng thử lại.", ex);
        }
        return verifyMatchingExistingSale(existingOpt.get(), cmd);
    }

    private ReceiptView verifyMatchingExistingSale(Membership existing, SaleCommand cmd) {
        Payment existingPayment = paymentRepository.findByMembershipId(existing.id())
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR,
                        "Phiếu thu của giao dịch trước chưa đầy đủ; vui lòng kiểm tra lại."));

        boolean matches = existing.memberId() == cmd.getMemberId()
                && existing.planId() == cmd.getPlanId()
                && existing.saleType() == cmd.getSaleType()
                && existing.kindSnapshot() == cmd.getQuotedKind()
                && existing.durationDaysSnapshot() == cmd.getQuotedDurationDays()
                && Objects.equals(existing.sessionsTotalSnapshot(), cmd.getQuotedSessionsTotal())
                && existing.priceVndSnapshot() == cmd.getQuotedPriceVnd()
                && existing.startDate().equals(cmd.getExpectedStart())
                && existing.endDate().equals(cmd.getExpectedEnd())
                && cmd.isCashConfirmed()
                && Objects.equals(cmd.getAmountReceivedVnd(), existingPayment.amountVnd());

        if (!matches) {
            throw new GymException(ErrorCode.REQUEST_CONFLICT,
                    "Mã yêu cầu (request_key) này đã được dùng cho một giao dịch khác. Vui lòng mở lại form bán mới.");
        }

        return paymentRepository.findReceiptByMembershipId(existing.id())
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    private void validateCommandBasics(SaleCommand cmd) {
        if (cmd == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thiếu thông tin giao dịch bán gói.");
        }
        if (!isValidUuid(cmd.getRequestKey())) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mã yêu cầu (request_key) không hợp lệ.");
        }
        if (cmd.getMemberId() == null || cmd.getMemberId() <= 0
                || cmd.getPlanId() == null || cmd.getPlanId() <= 0
                || cmd.getSaleType() == null
                || cmd.getQuotedKind() == null
                || cmd.getQuotedDurationDays() == null || cmd.getQuotedDurationDays() <= 0
                || cmd.getQuotedPriceVnd() == null || cmd.getQuotedPriceVnd() <= 0
                || cmd.getExpectedStart() == null || cmd.getExpectedEnd() == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Dữ liệu báo giá gửi lên không đầy đủ hoặc không hợp lệ.");
        }
        cmd.setRequestKey(cmd.getRequestKey().trim());
    }

    private boolean isValidUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(trimmed);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
