package vn.edu.gym.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.Plan;
import vn.edu.gym.model.PlanForm;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.Role;
import vn.edu.gym.repository.PlanRepository;
import vn.edu.gym.security.SecurityUtils;

import java.util.List;

@Service
public class PlanService {

    private static final int MAX_DAYS = 3650;
    private static final int MAX_SESSIONS = 1000;
    private static final long MAX_PRICE_VND = 100_000_000L;

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<Plan> listAll() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        return planRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<Plan> listActive() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        return planRepository.findAllActive();
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<Plan> listActiveByKind(PlanKind kind) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER, Role.RECEPTIONIST);
        return planRepository.findActiveByKind(kind);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Plan create(PlanForm form) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        ValidatedPlan valid = validate(form);
        long id = planRepository.insert(valid.name(), valid.kind(), valid.durationDays(), valid.sessionsTotal(), valid.priceVnd());
        return planRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Plan update(long id, PlanForm form) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        planRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy gói tập."));
        ValidatedPlan valid = validate(form);
        planRepository.update(id, valid.name(), valid.kind(), valid.durationDays(), valid.sessionsTotal(), valid.priceVnd());
        return planRepository.findById(id).orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setActive(long id, boolean active) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        planRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy gói tập."));
        planRepository.updateActive(id, active);
    }

    private ValidatedPlan validate(PlanForm form) {
        if (form == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thiếu thông tin gói tập.");
        }
        String name = form.getName() == null ? "" : form.getName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Tên gói tập phải dài từ 2 đến 100 ký tự.");
        }
        PlanKind kind = form.getKind();
        if (kind == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Loại gói phải là GYM hoặc PT.");
        }
        Integer duration = form.getDurationDays();
        if (duration == null || duration < 1 || duration > MAX_DAYS) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thời hạn ngày phải là số nguyên từ 1 đến 3.650.");
        }
        Long price = form.getPriceVnd();
        if (price == null || price < 1 || price > MAX_PRICE_VND) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Giá tiền phải là số nguyên từ 1 đến 100.000.000 VNĐ.");
        }
        Integer sessions = form.getSessionsTotal();
        if (kind == PlanKind.GYM) {
            if (sessions != null) {
                throw new GymException(ErrorCode.INVALID_INPUT, "Gói GYM theo ngày không được nhập số buổi PT.");
            }
        } else {
            if (sessions == null || sessions < 1 || sessions > MAX_SESSIONS) {
                throw new GymException(ErrorCode.INVALID_INPUT, "Gói PT bắt buộc có số buổi từ 1 đến 1.000.");
            }
        }
        return new ValidatedPlan(name, kind, duration, sessions, price);
    }

    private record ValidatedPlan(String name, PlanKind kind, int durationDays, Integer sessionsTotal, long priceVnd) {
    }
}
