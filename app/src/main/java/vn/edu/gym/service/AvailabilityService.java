package vn.edu.gym.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.AvailabilityForm;
import vn.edu.gym.model.AvailabilitySlot;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.PtBookingView;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.Trainer;
import vn.edu.gym.repository.AvailabilityRepository;
import vn.edu.gym.repository.BookingRepository;
import vn.edu.gym.repository.TrainerRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class AvailabilityService {

    private static final LocalTime OPEN_TIME = LocalTime.of(5, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TrainerRepository trainerRepository;
    private final AvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public AvailabilityService(
            TrainerRepository trainerRepository,
            AvailabilityRepository availabilityRepository,
            BookingRepository bookingRepository,
            Clock clock
    ) {
        this.trainerRepository = trainerRepository;
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST', 'TRAINER')")
    @Transactional(readOnly = true)
    public List<AvailabilitySlot> listForCurrentActor(Long trainerId, LocalDate date) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST, Role.TRAINER);
        Long effectiveTrainerId = trainerId;
        if (actor.role() == Role.TRAINER) {
            if (trainerId != null && !Objects.equals(actor.trainerId(), trainerId)) {
                throw new GymException(ErrorCode.FORBIDDEN, "Huấn luyện viên chỉ được xem khung giờ rảnh của chính mình.");
            }
            effectiveTrainerId = actor.trainerId();
        }
        return availabilityRepository.findFiltered(effectiveTrainerId, date);
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TRAINER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilitySlot create(long trainerId, AvailabilityForm form) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        checkManagePermission(actor, trainerId);

        Range range = validateRange(form);
        Trainer trainer = trainerRepository.findByIdForUpdate(trainerId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy huấn luyện viên."));
        if (!trainer.active()) {
            throw new GymException(ErrorCode.INVALID_STATE, "Huấn luyện viên đang ngừng nhận lịch.");
        }

        if (availabilityRepository.existsOverlapping(trainerId, range.startAt(), range.endAt(), null)) {
            throw new GymException(ErrorCode.AVAILABILITY_CONFLICT,
                    "Khung giờ rảnh bị chồng lấn với khung giờ đã khai báo của " + trainer.fullName() + ".");
        }

        long id = availabilityRepository.insert(trainerId, range.startAt(), range.endAt());
        return availabilityRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TRAINER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilitySlot update(long slotId, AvailabilityForm form) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        AvailabilitySlot header = availabilityRepository.findById(slotId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ rảnh."));
        checkManagePermission(actor, header.trainerId());

        Range range = validateRange(form);
        trainerRepository.findByIdForUpdate(header.trainerId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy huấn luyện viên."));
        AvailabilitySlot current = availabilityRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ rảnh."));

        ensureNoBookedInsideSlot(current);

        if (availabilityRepository.existsOverlapping(current.trainerId(), range.startAt(), range.endAt(), slotId)) {
            throw new GymException(ErrorCode.AVAILABILITY_CONFLICT,
                    "Khung giờ mới bị chồng lấn với khung giờ khác của cùng PT.");
        }

        availabilityRepository.update(slotId, range.startAt(), range.endAt());
        return availabilityRepository.findById(slotId).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TRAINER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long slotId) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        AvailabilitySlot header = availabilityRepository.findById(slotId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ rảnh."));
        checkManagePermission(actor, header.trainerId());

        trainerRepository.findByIdForUpdate(header.trainerId())
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy huấn luyện viên."));
        AvailabilitySlot current = availabilityRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ rảnh."));

        ensureNoBookedInsideSlot(current);
        availabilityRepository.delete(slotId);
    }

    private void checkManagePermission(CurrentUser actor, long targetTrainerId) {
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.TRAINER);
        if (actor.role() == Role.TRAINER && !Objects.equals(actor.trainerId(), targetTrainerId)) {
            throw new GymException(ErrorCode.FORBIDDEN, "Huấn luyện viên chỉ được chỉnh sửa khung giờ rảnh của chính mình.");
        }
    }

    private void ensureNoBookedInsideSlot(AvailabilitySlot slot) {
        List<PtBookingView> blocking = bookingRepository.findBookedOverlappingRangeForTrainer(
                slot.trainerId(),
                slot.startAt(),
                slot.endAt(),
                LocalDateTime.now(clock)
        );
        if (!blocking.isEmpty()) {
            List<String> details = blocking.stream()
                    .map(b -> b.bookingCode() + " · " + b.startAt().format(DT_FMT) + " · " + b.memberName())
                    .toList();
            throw new GymException(
                    ErrorCode.PENDING_BOOKINGS,
                    "Không thể sửa hoặc xóa khung giờ này vì có " + blocking.size()
                            + " lịch PT đang ở trạng thái Đã đặt (BOOKED) nằm trong khung giờ.",
                    details
            );
        }
    }

    private Range validateRange(AvailabilityForm form) {
        if (form == null || form.getDate() == null || form.getStartTime() == null || form.getEndTime() == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Vui lòng nhập đầy đủ ngày, giờ bắt đầu và giờ kết thúc.");
        }
        LocalDate date = form.getDate();
        LocalTime start = form.getStartTime();
        LocalTime end = form.getEndTime();
        if (!start.isBefore(end)) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Giờ bắt đầu phải sớm hơn giờ kết thúc trong cùng ngày.");
        }
        if (start.isBefore(OPEN_TIME) || end.isAfter(CLOSE_TIME)) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Khung giờ rảnh phải nằm trong giờ mở cửa từ 05:00 đến 22:00.");
        }
        return new Range(LocalDateTime.of(date, start), LocalDateTime.of(date, end));
    }

    private record Range(LocalDateTime startAt, LocalDateTime endAt) {
    }
}
