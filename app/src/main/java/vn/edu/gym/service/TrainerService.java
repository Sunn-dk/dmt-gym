package vn.edu.gym.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.PtBookingView;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.Trainer;
import vn.edu.gym.model.TrainerForm;
import vn.edu.gym.repository.BookingRepository;
import vn.edu.gym.repository.TrainerRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TrainerService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TrainerRepository trainerRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public TrainerService(
            TrainerRepository trainerRepository,
            BookingRepository bookingRepository,
            Clock clock
    ) {
        this.trainerRepository = trainerRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST', 'TRAINER')")
    @Transactional(readOnly = true)
    public List<Trainer> listForCurrentActor() {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST, Role.TRAINER);
        if (actor.role() == Role.TRAINER) {
            if (actor.trainerId() == null) {
                return List.of();
            }
            return trainerRepository.findById(actor.trainerId())
                    .map(List::of)
                    .orElse(List.of());
        }
        return trainerRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<Trainer> listAllActive() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        return trainerRepository.findAllActive();
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Trainer create(TrainerForm form) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        ValidatedTrainer valid = validate(form);
        long id = trainerRepository.insert(valid.fullName(), valid.specialty());
        return trainerRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Trainer update(long id, TrainerForm form) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        trainerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ huấn luyện viên."));
        ValidatedTrainer valid = validate(form);
        trainerRepository.update(id, valid.fullName(), valid.specialty());
        return trainerRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setActive(long id, boolean active) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        Trainer trainer = trainerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy huấn luyện viên."));

        if (!active) {
            List<PtBookingView> pending = bookingRepository.findPendingBookedByTrainer(id, LocalDateTime.now(clock));
            if (!pending.isEmpty()) {
                List<String> details = pending.stream()
                        .map(b -> b.bookingCode() + " · " + b.startAt().format(DT_FMT) + " · " + b.memberName()
                                + (b.overduePending() ? " (Quá giờ chưa chốt)" : " (Sắp diễn ra)"))
                        .toList();
                throw new GymException(
                        ErrorCode.PENDING_BOOKINGS,
                        "Không thể ngừng PT " + trainer.fullName()
                                + " vì còn " + pending.size() + " lịch ở trạng thái Đã đặt (BOOKED). "
                                + "Vui lòng hủy lịch tương lai hoặc chốt kết quả buổi đã qua giờ trước.",
                        details
                );
            }
        }
        trainerRepository.updateActive(id, active);
    }

    private ValidatedTrainer validate(TrainerForm form) {
        if (form == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thiếu thông tin huấn luyện viên.");
        }
        String fullName = form.getFullName() == null ? "" : form.getFullName().trim();
        if (fullName.length() < 2 || fullName.length() > 100) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Tên huấn luyện viên phải dài từ 2 đến 100 ký tự.");
        }
        String specialty = form.getSpecialty() == null ? "" : form.getSpecialty().trim();
        if (specialty.length() < 2 || specialty.length() > 150) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Chuyên môn phải dài từ 2 đến 150 ký tự.");
        }
        return new ValidatedTrainer(fullName, specialty);
    }

    private record ValidatedTrainer(String fullName, String specialty) {
    }
}
