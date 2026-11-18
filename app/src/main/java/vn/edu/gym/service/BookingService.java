package vn.edu.gym.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.BookingInput;
import vn.edu.gym.model.BookingStatus;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Member;
import vn.edu.gym.model.Membership;
import vn.edu.gym.model.MembershipDetailView;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.PtBooking;
import vn.edu.gym.model.PtBookingView;
import vn.edu.gym.model.QuotaView;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.Trainer;
import vn.edu.gym.model.VisitView;
import vn.edu.gym.repository.AvailabilityRepository;
import vn.edu.gym.repository.BookingRepository;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.repository.MembershipRepository;
import vn.edu.gym.repository.TrainerRepository;
import vn.edu.gym.repository.UserRepository;
import vn.edu.gym.repository.VisitRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class BookingService {

    public static final int PAGE_SIZE = 20;
    private static final LocalTime OPEN_TIME = LocalTime.of(5, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TrainerRepository trainerRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final AvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public BookingService(
            TrainerRepository trainerRepository,
            MemberRepository memberRepository,
            MembershipRepository membershipRepository,
            AvailabilityRepository availabilityRepository,
            BookingRepository bookingRepository,
            VisitRepository visitRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.trainerRepository = trainerRepository;
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST', 'TRAINER')")
    @Transactional(readOnly = true)
    public PageResult<PtBookingView> search(
            LocalDate date,
            Long trainerId,
            Long memberId,
            String statusFilter,
            int page
    ) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST, Role.TRAINER);
        Long effectiveTrainerId = actor.role() == Role.TRAINER ? actor.trainerId() : trainerId;
        if (actor.role() == Role.TRAINER && effectiveTrainerId == null) {
            return new PageResult<>(List.of(), 0, PAGE_SIZE, 0);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PageResult<PtBookingView> raw = bookingRepository.search(
                date, effectiveTrainerId, memberId, statusFilter, now, page, PAGE_SIZE
        );
        List<PtBookingView> enriched = raw.items().stream()
                .map(b -> enrichAttendance(b, now.toLocalDate()))
                .toList();
        return new PageResult<>(enriched, raw.page(), raw.pageSize(), raw.totalItems());
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST', 'TRAINER')")
    @Transactional(readOnly = true)
    public List<PtBookingView> listOverduePending() {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST, Role.TRAINER);
        Long effectiveTrainerId = actor.role() == Role.TRAINER ? actor.trainerId() : null;
        if (actor.role() == Role.TRAINER && effectiveTrainerId == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.findOverduePending(effectiveTrainerId, now).stream()
                .map(b -> enrichAttendance(b, now.toLocalDate()))
                .toList();
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<MembershipDetailView> listPtContractsForMember(long memberId) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        LocalDate today = LocalDate.now(clock);
        return membershipRepository.findPtByMemberId(memberId).stream()
                .map(ms -> {
                    int used = bookingRepository.countUsedByMembership(ms.id());
                    int reserved = bookingRepository.countReservedByMembership(ms.id());
                    QuotaView quota = QuotaView.compute(ms.sessionsTotalSnapshot(), used, reserved);
                    String soldByName = userRepository.findById(ms.soldBy()).map(u -> u.fullName()).orElse("");
                    return new MembershipDetailView(ms, ms.statusAt(today), ms.lastUsableDate(), quota, null, soldByName);
                })
                .toList();
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PtBookingView book(BookingInput input) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST);

        if (input == null || input.getMemberId() == null || input.getMembershipId() == null || input.getTrainerId() == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Vui lòng chọn đầy đủ hội viên, hợp đồng PT và huấn luyện viên.");
        }
        LocalDateTime startAt = input.resolveStartAt();
        if (startAt == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Vui lòng nhập ngày và giờ bắt đầu buổi tập.");
        }
        // Normalize seconds/nanos to zero since DB DATETIME(0) stores second precision
        startAt = startAt.withSecond(0).withNano(0);
        LocalDateTime endAt = startAt.plusMinutes(60);

        // Mandatory parent lock order (BR-09): trainers -> members -> memberships (PT)
        Trainer trainer = trainerRepository.findByIdForUpdate(input.getTrainerId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy huấn luyện viên."));
        Member member = memberRepository.findByIdForUpdate(input.getMemberId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hội viên."));

        Membership ptMembership = membershipRepository.findByIdAndMemberForUpdate(input.getMembershipId(), member.id())
                .orElseThrow(() -> {
                    boolean existsElsewhere = membershipRepository.findById(input.getMembershipId()).isPresent();
                    if (existsElsewhere) {
                        return new GymException(ErrorCode.INELIGIBLE_BOOKING,
                                "Hợp đồng PT #" + input.getMembershipId() + " không thuộc về hội viên " + member.memberCode() + ".");
                    }
                    return new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hợp đồng PT.");
                });

        LocalDateTime now = LocalDateTime.now(clock);

        // Idempotent exact duplicate check for BOOKED
        var exactDup = bookingRepository.findExactBookedDuplicate(
                member.id(), ptMembership.id(), trainer.id(), startAt, endAt
        );
        if (exactDup.isPresent()) {
            return bookingRepository.findViewById(exactDup.get().id(), now)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        }

        // Eligibility checks (BR-16)
        if (!trainer.active()) {
            throw new GymException(ErrorCode.INELIGIBLE_BOOKING, "Huấn luyện viên " + trainer.fullName() + " đang ngừng nhận lịch.");
        }
        if (!member.active()) {
            throw new GymException(ErrorCode.INELIGIBLE_BOOKING, "Hội viên " + member.memberCode() + " đang ở trạng thái ngừng phục vụ.");
        }
        if (ptMembership.kindSnapshot() != PlanKind.PT) {
            throw new GymException(ErrorCode.INELIGIBLE_BOOKING,
                    "Hợp đồng được chọn (" + ptMembership.planNameSnapshot() + ") không phải gói PT.");
        }
        LocalDate bookingDate = startAt.toLocalDate();
        if (bookingDate.isBefore(ptMembership.startDate()) || !bookingDate.isBefore(ptMembership.endDate())) {
            throw new GymException(ErrorCode.INELIGIBLE_BOOKING,
                    "Ngày tập " + bookingDate.format(D_FMT) + " nằm ngoài thời hạn hợp đồng PT ("
                            + ptMembership.startDate().format(D_FMT) + " – "
                            + ptMembership.lastUsableDate().format(D_FMT) + ").");
        }
        if (!membershipRepository.existsActiveGymAtDate(member.id(), bookingDate)) {
            throw new GymException(ErrorCode.INELIGIBLE_BOOKING,
                    "Hội viên " + member.memberCode() + " chưa có hợp đồng GYM hiệu lực vào ngày "
                            + bookingDate.format(D_FMT) + " (gói PT không tự cấp quyền vào phòng gym).");
        }

        // Time and trainer availability checks (BR-18)
        if (!startAt.isAfter(now)) {
            throw new GymException(ErrorCode.OUTSIDE_AVAILABILITY,
                    "Buổi PT phải được đặt ở thời điểm tương lai (sau " + now.format(DT_FMT) + ").");
        }
        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new GymException(ErrorCode.OUTSIDE_AVAILABILITY,
                    "Buổi PT 60 phút phải bắt đầu và kết thúc trong cùng một ngày.");
        }
        LocalTime st = startAt.toLocalTime();
        LocalTime et = endAt.toLocalTime();
        if (st.isBefore(OPEN_TIME) || et.isAfter(CLOSE_TIME)) {
            throw new GymException(ErrorCode.OUTSIDE_AVAILABILITY,
                    "Buổi PT 60 phút phải nằm trong khung giờ mở cửa 05:00 – 22:00.");
        }
        if (!availabilityRepository.existsContainingRange(trainer.id(), startAt, endAt)) {
            throw new GymException(ErrorCode.OUTSIDE_AVAILABILITY,
                    "Khung giờ " + startAt.format(DT_FMT) + " – " + et + " không nằm trọn trong giờ rảnh đã khai báo của "
                            + trainer.fullName() + ".");
        }

        // Overlap checks (BR-19)
        if (!bookingRepository.findTrainerConflicts(trainer.id(), startAt, endAt).isEmpty()) {
            throw new GymException(ErrorCode.TRAINER_CONFLICT,
                    trainer.fullName() + " đã có lịch BOOKED giao thời gian với " + startAt.format(DT_FMT) + " – " + et + ".");
        }
        if (!bookingRepository.findMemberConflicts(member.id(), startAt, endAt).isEmpty()) {
            throw new GymException(ErrorCode.MEMBER_CONFLICT,
                    "Hội viên " + member.memberCode() + " đã có một lịch PT khác trùng khung giờ "
                            + startAt.format(DT_FMT) + " – " + et + ".");
        }

        // Quota check (BR-17) computed from DB under locks
        int used = bookingRepository.countUsedByMembership(ptMembership.id());
        int reserved = bookingRepository.countReservedByMembership(ptMembership.id());
        int total = ptMembership.sessionsTotalSnapshot();
        int available = total - used - reserved;
        if (available < 1) {
            throw new GymException(ErrorCode.NO_PT_CREDIT,
                    "Hợp đồng PT này đã hết quyền đặt mới (Tổng " + total + ", Đã dùng " + used
                            + ", Đang giữ " + reserved + ", Còn đặt " + available + ").");
        }

        long id = bookingRepository.insertBooked(member.id(), ptMembership.id(), trainer.id(), startAt, endAt, now, actor.id());
        return bookingRepository.findViewById(id, now)
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PtBookingView cancel(long bookingId) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST);

        PtBooking header = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy lịch PT BK-" + bookingId + "."));

        // Lock order: trainers -> members -> memberships -> pt_bookings
        trainerRepository.findByIdForUpdate(header.trainerId());
        memberRepository.findByIdForUpdate(header.memberId());
        membershipRepository.findByIdAndMemberForUpdate(header.membershipId(), header.memberId());
        PtBooking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy lịch PT BK-" + bookingId + "."));

        LocalDateTime now = LocalDateTime.now(clock);
        if (booking.status() == BookingStatus.CANCELLED) {
            return bookingRepository.findViewById(bookingId, now)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        }
        if (booking.status() != BookingStatus.BOOKED) {
            throw new GymException(ErrorCode.INVALID_STATE,
                    "Không thể hủy lịch PT đã ở trạng thái cuối (" + booking.status().getLabel() + ").");
        }
        if (!now.isBefore(booking.startAt())) {
            throw new GymException(ErrorCode.CANCELLATION_TOO_LATE,
                    "Chỉ được hủy lịch trước giờ bắt đầu (" + booking.startAt().format(DT_FMT) + ").");
        }

        bookingRepository.resolveIfBooked(bookingId, BookingStatus.CANCELLED, now, actor.id());
        return bookingRepository.findViewById(bookingId, now)
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TRAINER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PtBookingView complete(long bookingId) {
        return resolveSession(bookingId, BookingStatus.COMPLETED);
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TRAINER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PtBookingView markNoShow(long bookingId) {
        return resolveSession(bookingId, BookingStatus.NO_SHOW);
    }

    private PtBookingView resolveSession(long bookingId, BookingStatus targetStatus) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.TRAINER);

        PtBooking header = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy lịch PT BK-" + bookingId + "."));

        if (actor.role() == Role.TRAINER && !Objects.equals(actor.trainerId(), header.trainerId())) {
            throw new GymException(ErrorCode.FORBIDDEN, "Huấn luyện viên chỉ được chốt kết quả các buổi PT do mình phụ trách.");
        }

        // Lock order: trainers -> members -> memberships -> pt_bookings
        trainerRepository.findByIdForUpdate(header.trainerId());
        memberRepository.findByIdForUpdate(header.memberId());
        membershipRepository.findByIdAndMemberForUpdate(header.membershipId(), header.memberId());
        PtBooking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy lịch PT BK-" + bookingId + "."));

        LocalDateTime now = LocalDateTime.now(clock);
        if (booking.status() == targetStatus) {
            return bookingRepository.findViewById(bookingId, now)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        }
        if (booking.status() != BookingStatus.BOOKED) {
            throw new GymException(ErrorCode.INVALID_STATE,
                    "Buổi PT đã chốt kết quả " + booking.status().getLabel() + ", không thể đổi sang " + targetStatus.getLabel() + ".");
        }
        if (now.isBefore(booking.endAt())) {
            throw new GymException(ErrorCode.SESSION_NOT_ENDED,
                    "Buổi PT kết thúc lúc " + booking.endAt().format(DT_FMT) + "; chưa đến thời điểm chốt kết quả.");
        }

        if (targetStatus == BookingStatus.COMPLETED) {
            boolean hasAttendance = visitRepository.existsOverlappingVisit(
                    booking.memberId(), booking.startAt(), booking.endAt()
            );
            if (!hasAttendance) {
                throw new GymException(ErrorCode.NO_ATTENDANCE,
                        "Không thể chốt Hoàn thành vì hội viên chưa có lượt check-in GYM giao với khung giờ buổi tập ("
                                + booking.startAt().format(DT_FMT) + " – " + booking.endAt().toLocalTime() + ").");
            }
        }

        bookingRepository.resolveIfBooked(bookingId, targetStatus, now, actor.id());
        return bookingRepository.findViewById(bookingId, now)
                .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    private PtBookingView enrichAttendance(PtBookingView view, LocalDate today) {
        if (view.status() != BookingStatus.BOOKED) {
            return view;
        }
        List<VisitView> visits = visitRepository.findOverlappingVisits(
                view.memberId(), view.startAt(), view.endAt(), today
        );
        if (visits.isEmpty()) {
            return new PtBookingView(
                    view.id(), view.memberId(), view.memberName(), view.membershipId(), view.planNameSnapshot(),
                    view.trainerId(), view.trainerName(), view.startAt(), view.endAt(), view.status(),
                    view.overduePending(), view.canResolveNow(),
                    false, "Chưa có visit GYM giao giờ tập",
                    view.createdAt(), view.createdBy(), view.createdByName(),
                    view.resolvedAt(), view.resolvedBy(), view.resolvedByName()
            );
        }
        VisitView v = visits.get(0);
        String outStr = v.checkOutAt() == null ? "Đang mở" : v.checkOutAt().toLocalTime().toString();
        String summary = "Có visit #" + v.id() + " (" + v.checkInAt().format(DT_FMT) + " → " + outStr + ")";
        return new PtBookingView(
                view.id(), view.memberId(), view.memberName(), view.membershipId(), view.planNameSnapshot(),
                view.trainerId(), view.trainerName(), view.startAt(), view.endAt(), view.status(),
                view.overduePending(), view.canResolveNow(),
                true, summary,
                view.createdAt(), view.createdBy(), view.createdByName(),
                view.resolvedAt(), view.resolvedBy(), view.resolvedByName()
        );
    }
}
