package vn.edu.gym.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Member;
import vn.edu.gym.model.MemberForm;
import vn.edu.gym.model.MemberListItem;
import vn.edu.gym.model.Membership;
import vn.edu.gym.model.MembershipDetailView;
import vn.edu.gym.model.MembershipStatus;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.PtBookingView;
import vn.edu.gym.model.QuotaView;
import vn.edu.gym.model.Role;
import vn.edu.gym.repository.BookingRepository;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.repository.MembershipRepository;
import vn.edu.gym.repository.PaymentRepository;
import vn.edu.gym.repository.UserRepository;
import vn.edu.gym.repository.VisitRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MemberService {

    public static final int PAGE_SIZE = 20;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0[0-9]{9}$");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MemberService(
            MemberRepository memberRepository,
            MembershipRepository membershipRepository,
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            VisitRepository visitRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResult<MemberListItem> search(String keyword, int page) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        LocalDate today = LocalDate.now(clock);
        PageResult<Member> rawPage = memberRepository.search(keyword, page, PAGE_SIZE);
        List<MemberListItem> enriched = rawPage.items().stream()
                .map(m -> toListItem(m, today))
                .toList();
        return new PageResult<>(enriched, rawPage.page(), rawPage.pageSize(), rawPage.totalItems());
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public MemberDetailResult getDetail(long memberId) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên #" + memberId + "."));
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        List<Membership> memberships = membershipRepository.findByMemberId(memberId);
        List<MembershipDetailView> contractViews = memberships.stream()
                .map(ms -> toMembershipDetail(ms, today))
                .toList();

        boolean hasAnyGymHistory = memberships.stream().anyMatch(ms -> ms.kindSnapshot() == PlanKind.GYM);
        boolean hasFutureOrActiveGym = memberships.stream()
                .anyMatch(ms -> ms.kindSnapshot() == PlanKind.GYM && ms.endDate().isAfter(today));
        boolean hasOpenVisit = visitRepository.hasOpenVisit(memberId);
        List<PtBookingView> pendingBookings = bookingRepository.findPendingBookedByMember(memberId, now);

        return new MemberDetailResult(
                member,
                contractViews,
                hasAnyGymHistory,
                hasFutureOrActiveGym,
                hasOpenVisit,
                pendingBookings
        );
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Member create(MemberForm form) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST);

        ValidatedMemberInput valid = validateForm(form);
        if (memberRepository.existsByPhone(valid.phone(), null)) {
            throw new GymException(ErrorCode.DUPLICATE_PHONE,
                    "Số điện thoại " + valid.phone() + " đã thuộc một hồ sơ hội viên khác.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        try {
            long id = memberRepository.insert(valid.fullName(), valid.phone(), valid.birthDate(), now, actor.id());
            return memberRepository.findById(id)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        } catch (DuplicateKeyException ex) {
            throw new GymException(ErrorCode.DUPLICATE_PHONE,
                    "Số điện thoại " + valid.phone() + " đã tồn tại.", ex);
        }
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Member update(long id, MemberForm form) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        memberRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên."));

        ValidatedMemberInput valid = validateForm(form);
        if (memberRepository.existsByPhone(valid.phone(), id)) {
            throw new GymException(ErrorCode.DUPLICATE_PHONE,
                    "Số điện thoại " + valid.phone() + " đã thuộc một hồ sơ hội viên khác.");
        }

        try {
            memberRepository.update(id, valid.fullName(), valid.phone(), valid.birthDate());
            return memberRepository.findById(id)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        } catch (DuplicateKeyException ex) {
            throw new GymException(ErrorCode.DUPLICATE_PHONE,
                    "Số điện thoại " + valid.phone() + " đã tồn tại.", ex);
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setActive(long id, boolean active) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        Member member = memberRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên."));

        if (!active) {
            List<PtBookingView> pending = bookingRepository.findPendingBookedByMember(id, LocalDateTime.now(clock));
            if (!pending.isEmpty()) {
                List<String> details = pending.stream()
                        .map(b -> b.bookingCode() + " · " + b.startAt().format(DT_FMT) + " · " + b.trainerName()
                                + (b.overduePending() ? " (Quá giờ chưa chốt)" : " (Sắp diễn ra)"))
                        .toList();
                throw new GymException(
                        ErrorCode.PENDING_BOOKINGS,
                        "Không thể ngừng phục vụ hội viên " + member.memberCode()
                                + " vì còn " + pending.size() + " lịch PT ở trạng thái Đã đặt (BOOKED). "
                                + "Vui lòng hủy lịch tương lai hoặc chốt buổi đã kết thúc trước.",
                        details
                );
            }
        }
        memberRepository.updateActive(id, active);
    }

    private ValidatedMemberInput validateForm(MemberForm form) {
        if (form == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thiếu thông tin hội viên.");
        }
        String fullName = form.getFullName() == null ? "" : form.getFullName().trim();
        if (fullName.length() < 2 || fullName.length() > 100) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Họ tên hội viên phải dài từ 2 đến 100 ký tự.");
        }
        String phone = form.getPhone() == null ? "" : form.getPhone().trim();
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0.");
        }
        LocalDate birthDate = form.getBirthDate();
        if (birthDate == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Vui lòng nhập ngày sinh hội viên.");
        }
        LocalDate today = LocalDate.now(clock);
        if (birthDate.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Ngày sinh không hợp lệ.");
        }
        if (birthDate.isAfter(today.minusYears(18))) {
            throw new GymException(ErrorCode.INVALID_INPUT,
                    "Hội viên phải đủ 18 tuổi tại ngày đăng ký/cập nhật (" + today.format(D_FMT) + ").");
        }
        return new ValidatedMemberInput(fullName, phone, birthDate);
    }

    private MemberListItem toListItem(Member member, LocalDate today) {
        List<Membership> all = membershipRepository.findByMemberId(member.id());
        String gymSummary = summarizeGym(all, today);
        String ptSummary = summarizePt(all, today);
        boolean openVisit = visitRepository.hasOpenVisit(member.id());
        return new MemberListItem(member, gymSummary, ptSummary, openVisit);
    }

    private String summarizeGym(List<Membership> all, LocalDate today) {
        List<Membership> gyms = all.stream().filter(m -> m.kindSnapshot() == PlanKind.GYM).toList();
        if (gyms.isEmpty()) {
            return "Chưa mua GYM";
        }
        return gyms.stream()
                .filter(m -> m.statusAt(today) == MembershipStatus.ACTIVE)
                .findFirst()
                .map(active -> {
                    LocalDate maxEnd = gyms.stream().map(Membership::endDate).max(LocalDate::compareTo).orElse(active.endDate());
                    return "Đang hiệu lực (hạn cuối " + maxEnd.minusDays(1).format(D_FMT) + ")";
                })
                .orElseGet(() -> gyms.stream()
                        .filter(m -> m.statusAt(today) == MembershipStatus.SCHEDULED)
                        .findFirst()
                        .map(sched -> "Sắp hiệu lực từ " + sched.startDate().format(D_FMT))
                        .orElse("Đã hết hạn GYM"));
    }

    private String summarizePt(List<Membership> all, LocalDate today) {
        List<Membership> pts = all.stream().filter(m -> m.kindSnapshot() == PlanKind.PT).toList();
        if (pts.isEmpty()) {
            return "Không có PT";
        }
        int activeContracts = 0;
        int totalAvail = 0;
        for (Membership pt : pts) {
            if (pt.statusAt(today) == MembershipStatus.ACTIVE) {
                activeContracts++;
                int used = bookingRepository.countUsedByMembership(pt.id());
                int reserved = bookingRepository.countReservedByMembership(pt.id());
                totalAvail += (pt.sessionsTotalSnapshot() - used - reserved);
            }
        }
        if (activeContracts == 0) {
            return pts.size() + " hợp đồng PT (hết hạn/chưa tới)";
        }
        return activeContracts + " HĐ hiệu lực · còn đặt " + totalAvail + " buổi";
    }

    private MembershipDetailView toMembershipDetail(Membership ms, LocalDate today) {
        QuotaView quota = null;
        if (ms.kindSnapshot() == PlanKind.PT && ms.sessionsTotalSnapshot() != null) {
            int used = bookingRepository.countUsedByMembership(ms.id());
            int reserved = bookingRepository.countReservedByMembership(ms.id());
            quota = QuotaView.compute(ms.sessionsTotalSnapshot(), used, reserved);
        }
        Long paymentId = paymentRepository.findByMembershipId(ms.id()).map(p -> p.id()).orElse(null);
        String soldByName = userRepository.findById(ms.soldBy()).map(u -> u.fullName()).orElse("NV #" + ms.soldBy());
        return new MembershipDetailView(
                ms,
                ms.statusAt(today),
                ms.lastUsableDate(),
                quota,
                paymentId,
                soldByName
        );
    }

    private record ValidatedMemberInput(String fullName, String phone, LocalDate birthDate) {
    }

    public record MemberDetailResult(
            Member member,
            List<MembershipDetailView> memberships,
            boolean hasAnyGymHistory,
            boolean hasFutureOrActiveGym,
            boolean hasOpenVisit,
            List<PtBookingView> pendingBookings
    ) {
    }
}
