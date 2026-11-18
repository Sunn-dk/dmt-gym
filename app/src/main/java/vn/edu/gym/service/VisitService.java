package vn.edu.gym.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Member;
import vn.edu.gym.model.Membership;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.Visit;
import vn.edu.gym.model.VisitView;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.repository.MembershipRepository;
import vn.edu.gym.repository.VisitRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VisitService {

    public static final int PAGE_SIZE = 20;
    private static final LocalTime OPEN_TIME = LocalTime.of(5, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final VisitRepository visitRepository;
    private final TransactionTemplate readCommittedTx;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;

    public VisitService(
            MemberRepository memberRepository,
            MembershipRepository membershipRepository,
            VisitRepository visitRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.visitRepository = visitRepository;
        this.clock = clock;

        this.readCommittedTx = new TransactionTemplate(transactionManager);
        this.readCommittedTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTx.setReadOnly(true);
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<VisitView> listOpenVisits() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        return visitRepository.findOpenVisits(LocalDate.now(clock));
    }

    public static final int DEFAULT_HISTORY_LOOKBACK_DAYS = 30;

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResult<VisitView> search(Long memberId, String keyword, LocalDate fromDate, LocalDate toDate, int page) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        LocalDate today = LocalDate.now(clock);
        DateRangeUtils.DateRange range = DateRangeUtils.resolveBoundedRange(
                fromDate,
                toDate,
                today.minusDays(DEFAULT_HISTORY_LOOKBACK_DAYS),
                today,
                DateRangeUtils.MAX_FILTER_DAYS
        );
        return visitRepository.search(memberId, keyword, range.fromDate(), range.toDate(), today, page, PAGE_SIZE);
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public VisitView checkIn(long memberId) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST);

        try {
            return readCommittedTx.execute(status -> checkInInTx(memberId, actor));
        } catch (DuplicateKeyException ex) {
            // Concurrent check-in raced on uq_open_visit: rollback then surface current open visit
            throw readOnlyTx.execute(status -> buildAlreadyInsideException(memberId, ex));
        }
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitView checkOut(long visitId) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST);

        Visit existingHeader = visitRepository.findById(visitId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy lượt ra/vào #" + visitId + "."));

        memberRepository.findByIdForUpdate(existingHeader.memberId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên."));

        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy lượt ra/vào #" + visitId + "."));

        LocalDate today = LocalDate.now(clock);
        if (!visit.isOpen()) {
            return visitRepository.findViewById(visitId, today)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(visit.checkInAt())) {
            throw new GymException(ErrorCode.CLOCK_ERROR,
                    "Giờ hệ thống (" + now.format(DT_FMT) + ") đang trước giờ check-in ("
                            + visit.checkInAt().format(DT_FMT) + ").");
        }

        visitRepository.updateCheckOutIfOpen(visitId, now, actor.id());
        return visitRepository.findViewById(visitId, today)
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    private VisitView checkInInTx(long memberId, CurrentUser actor) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên #" + memberId + "."));

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        var openOpt = visitRepository.findOpenVisitByMemberForUpdate(memberId);
        if (openOpt.isPresent()) {
            throw createOpenVisitConflict(member, openOpt.get(), today, null);
        }

        if (!member.active()) {
            throw new GymException(ErrorCode.MEMBER_INACTIVE,
                    "Hội viên " + member.memberCode() + " đang ngừng phục vụ, không thể check-in.");
        }

        LocalTime time = now.toLocalTime();
        if (time.isBefore(OPEN_TIME) || !time.isBefore(CLOSE_TIME)) {
            throw new GymException(ErrorCode.OUTSIDE_OPENING_HOURS,
                    "Chỉ được check-in trong khung giờ mở cửa [05:00, 22:00) (hiện tại: " + time + ").");
        }

        Membership activeGym = membershipRepository.findActiveGymAtDate(memberId, today)
                .orElseThrow(() -> new GymException(ErrorCode.NO_ACTIVE_MEMBERSHIP,
                        "Hội viên " + member.memberCode() + " không có hợp đồng GYM hiệu lực hôm nay (gói PT không cấp quyền vào cửa)."));

        long visitId = visitRepository.insertCheckIn(memberId, activeGym.id(), now, actor.id());
        return visitRepository.findViewById(visitId, today)
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    private GymException buildAlreadyInsideException(long memberId, DuplicateKeyException ex) {
        Member member = memberRepository.findById(memberId).orElse(null);
        LocalDate today = LocalDate.now(clock);
        var openOpt = visitRepository.findOpenVisitByMemberForUpdate(memberId);
        if (openOpt.isPresent() && member != null) {
            return createOpenVisitConflict(member, openOpt.get(), today, ex);
        }
        return new GymException(ErrorCode.ALREADY_INSIDE, ErrorCode.ALREADY_INSIDE.getDefaultMessage(), ex);
    }

    private GymException createOpenVisitConflict(
            Member member,
            Visit openVisit,
            LocalDate today,
            Throwable cause
    ) {
        boolean stale = openVisit.checkInAt().toLocalDate().isBefore(today);
        String msg = stale
                ? "Hội viên " + member.memberCode() + " còn lượt mở tồn đọng từ ngày trước (lượt #" + openVisit.id()
                + " vào lúc " + openVisit.checkInAt().format(DT_FMT)
                + "). Vui lòng xác minh và bấm Check-out đóng lượt cũ trước khi cho vào mới."
                : "Hội viên " + member.memberCode() + " đang có lượt tập chưa check-out hôm nay (lượt #" + openVisit.id()
                + " vào lúc " + openVisit.checkInAt().format(DT_FMT) + ").";
        return new GymException(ErrorCode.ALREADY_INSIDE, msg, List.of(), openVisit.id(), cause);
    }
}
