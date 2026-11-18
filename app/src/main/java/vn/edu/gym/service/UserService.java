package vn.edu.gym.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.Trainer;
import vn.edu.gym.model.UserAccount;
import vn.edu.gym.model.UserCreateInput;
import vn.edu.gym.repository.TrainerRepository;
import vn.edu.gym.repository.UserRepository;
import vn.edu.gym.security.SecurityUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,50}$");

    private final UserRepository userRepository;
    private final TrainerRepository trainerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            TrainerRepository trainerRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.trainerRepository = trainerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<UserAccount> listAll() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        return userRepository.findAll();
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<Trainer> listAvailableTrainersForUserCreation() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        return trainerRepository.findUnlinkedActiveTrainers();
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserAccount create(UserCreateInput input) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        if (input == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Thiếu thông tin tạo tài khoản.");
        }
        String username = input.getUsername() == null ? "" : input.getUsername().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new GymException(ErrorCode.INVALID_INPUT,
                    "Username phải dài 3–50 ký tự và chỉ gồm chữ thường [a-z], số [0-9], '.', '_', '-'.");
        }
        String fullName = validateFullName(input.getFullName());
        validateRawPassword(input.getPassword());

        Role role = input.getRole();
        if (role == null) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Vai trò tài khoản không hợp lệ.");
        }

        Long trainerId = input.getTrainerId();
        if (role == Role.TRAINER) {
            if (trainerId == null) {
                throw new GymException(ErrorCode.INVALID_INPUT, "Tài khoản TRAINER bắt buộc chọn hồ sơ PT liên kết.");
            }
            Trainer trainer = trainerRepository.findByIdForUpdate(trainerId)
                    .orElseThrow(() -> new GymException(ErrorCode.INVALID_INPUT, "Hồ sơ PT liên kết không tồn tại."));
            if (!trainer.active()) {
                throw new GymException(ErrorCode.INVALID_INPUT, "Chỉ được gắn tài khoản TRAINER vào hồ sơ PT đang hoạt động.");
            }
            if (userRepository.existsByTrainerId(trainerId)) {
                throw new GymException(ErrorCode.INVALID_INPUT, "Hồ sơ PT này đã được gắn với một tài khoản khác.");
            }
        } else {
            if (trainerId != null) {
                throw new GymException(ErrorCode.INVALID_INPUT, "Chỉ vai trò TRAINER mới được gắn hồ sơ PT.");
            }
        }

        if (userRepository.existsByUsername(username)) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Username '" + username + "' đã tồn tại.");
        }

        String hash = passwordEncoder.encode(input.getPassword());
        try {
            long id = userRepository.insert(username, hash, fullName, role, trainerId);
            return userRepository.findById(id)
                    .orElseThrow(() -> new GymException(ErrorCode.STORAGE_ERROR));
        } catch (DuplicateKeyException ex) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Username hoặc hồ sơ PT liên kết đã tồn tại.", ex);
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateName(long id, String fullName) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        String validName = validateFullName(fullName);
        userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));
        userRepository.updateFullName(id, validName);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setActive(long id, boolean active) {
        CurrentUser actor = SecurityUtils.requireCurrentUser();
        SecurityUtils.requireAnyRole(actor, Role.MANAGER);

        // Always lock MANAGER rows in ascending ID order first to prevent dual-manager lock race (BR-26 / TC-27)
        List<UserAccount> lockedManagers = userRepository.findManagersForUpdate();
        UserAccount target = lockedManagers.stream()
                .filter(u -> u.id() == id)
                .findFirst()
                .orElseGet(() -> userRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản.")));

        if (!active) {
            if (target.id() == actor.id()) {
                throw new GymException(ErrorCode.LAST_MANAGER_OR_SELF,
                        "Không thể tự khóa tài khoản Quản lý đang đăng nhập.");
            }
            if (target.role() == Role.MANAGER && target.active()) {
                long activeManagerCount = lockedManagers.stream().filter(UserAccount::active).count();
                if (activeManagerCount <= 1) {
                    throw new GymException(ErrorCode.LAST_MANAGER_OR_SELF,
                            "Không thể khóa tài khoản Quản lý đang hoạt động cuối cùng.");
                }
            }
        }

        if (target.active() != active) {
            userRepository.updateActiveAndBumpVersion(id, active);
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resetPassword(long id, String newPassword) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        validateRawPassword(newPassword);
        userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GymException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));
        String hash = passwordEncoder.encode(newPassword);
        userRepository.updatePasswordAndBumpVersion(id, hash);
    }

    private String validateFullName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Tên hiển thị phải dài từ 2 đến 100 ký tự.");
        }
        return trimmed;
    }

    private void validateRawPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Mật khẩu không được để trống.");
        }
        int byteLen = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen < 8 || byteLen > 72) {
            throw new GymException(ErrorCode.INVALID_INPUT,
                    "Mật khẩu phải dài từ 8 đến 72 byte UTF-8 (hiện tại " + byteLen + " byte).");
        }
    }
}
