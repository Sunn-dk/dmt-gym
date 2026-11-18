package vn.edu.gym.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Equipment;
import vn.edu.gym.model.EquipmentForm;
import vn.edu.gym.model.EquipmentStatus;
import vn.edu.gym.model.Role;
import vn.edu.gym.repository.EquipmentRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final Clock clock;

    public EquipmentService(EquipmentRepository equipmentRepository, Clock clock) {
        this.equipmentRepository = equipmentRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST', 'TRAINER')")
    @Transactional(readOnly = true)
    public List<Equipment> listAll(String keyword) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST, Role.TRAINER);
        return equipmentRepository.findAll(keyword);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Equipment create(EquipmentForm form) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER);
        ValidatedInfo valid = validateInfo(form);

        if (equipmentRepository.existsByCode(valid.code(), null)) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mã thiết bị '" + valid.code() + "' đã tồn tại.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            long id = equipmentRepository.insert(valid.code(), valid.name(), valid.location(), now, actor.id());
            return equipmentRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        } catch (DuplicateKeyException ex) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mã thiết bị '" + valid.code() + "' đã tồn tại.", ex);
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Equipment update(long id, EquipmentForm form) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER);
        equipmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy thiết bị."));
        ValidatedInfo valid = validateInfo(form);

        if (equipmentRepository.existsByCode(valid.code(), id)) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mã thiết bị '" + valid.code() + "' đã tồn tại.");
        }
        try {
            equipmentRepository.updateInfo(id, valid.code(), valid.name(), valid.location(), LocalDateTime.now(clock), actor.id());
            return equipmentRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        } catch (DuplicateKeyException ex) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mã thiết bị '" + valid.code() + "' đã tồn tại.", ex);
        }
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST', 'TRAINER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Equipment reportBroken(long id, String note) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER, Role.RECEPTIONIST, Role.TRAINER);
        String validNote = validateRequiredNote(note);

        Equipment eq = equipmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy thiết bị."));
        if (eq.status() != EquipmentStatus.AVAILABLE) {
            throw new GymException(ErrorCode.INVALID_STATE,
                    "Chỉ báo hỏng được thiết bị đang Sẵn sàng (hiện tại: " + eq.status().getLabel() + ").");
        }
        equipmentRepository.updateStatus(id, EquipmentStatus.BROKEN, validNote, LocalDateTime.now(clock), actor.id());
        return equipmentRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Equipment changeStatus(long id, EquipmentStatus targetStatus, String note) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER);
        if (targetStatus == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Trạng thái đích không hợp lệ.");
        }
        String validNote = validateRequiredNote(note);

        Equipment eq = equipmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy thiết bị."));

        boolean validTransition =
                (eq.status() == EquipmentStatus.BROKEN && targetStatus == EquipmentStatus.MAINTENANCE)
                        || (eq.status() == EquipmentStatus.BROKEN && targetStatus == EquipmentStatus.AVAILABLE)
                        || (eq.status() == EquipmentStatus.MAINTENANCE && targetStatus == EquipmentStatus.AVAILABLE);

        if (!validTransition) {
            throw new GymException(ErrorCode.INVALID_STATE,
                    "Không thể chuyển trạng thái từ " + eq.status().getLabel() + " sang " + targetStatus.getLabel() + ".");
        }

        equipmentRepository.updateStatus(id, targetStatus, validNote, LocalDateTime.now(clock), actor.id());
        return equipmentRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    private ValidatedInfo validateInfo(EquipmentForm form) {
        if (form == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thiếu thông tin thiết bị.");
        }
        String code = form.getCode() == null ? "" : form.getCode().trim();
        if (code.length() < 2 || code.length() > 30) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mã thiết bị phải dài từ 2 đến 30 ký tự.");
        }
        String name = form.getName() == null ? "" : form.getName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Tên thiết bị phải dài từ 2 đến 100 ký tự.");
        }
        String location = form.getLocation() == null ? "" : form.getLocation().trim();
        if (location.isEmpty() || location.length() > 100) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Vị trí thiết bị bắt buộc nhập (tối đa 100 ký tự).");
        }
        return new ValidatedInfo(code, name, location);
    }

    private String validateRequiredNote(String rawNote) {
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.isEmpty() || note.length() > 255) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Bắt buộc nhập ghi chú xử lý (1–255 ký tự).");
        }
        return note;
    }

    private record ValidatedInfo(String code, String name, String location) {
    }
}
