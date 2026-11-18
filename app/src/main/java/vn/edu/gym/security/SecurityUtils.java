package vn.edu.gym.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Role;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<CurrentUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof GymUserDetails details) {
            return Optional.of(details.getCurrentUser());
        }
        return Optional.empty();
    }

    public static CurrentUser requireCurrentUser() {
        return currentUser()
                .orElseThrow(() -> new GymException(ErrorCode.FORBIDDEN, "Vui lòng đăng nhập để tiếp tục."));
    }

    public static void requireAnyRole(CurrentUser user, Role... allowed) {
        if (user == null) {
            throw new GymException(ErrorCode.FORBIDDEN);
        }
        for (Role r : allowed) {
            if (user.role() == r) {
                return;
            }
        }
        throw new GymException(ErrorCode.FORBIDDEN);
    }
}
