package vn.edu.gym.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.UserAccount;
import vn.edu.gym.repository.UserRepository;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Verifies users.active and users.credential_version against the DB on every request (BR-26 / NFR-02).
 * Immediately invalidates stale sessions after user lock or password reset.
 */
@Component
public class CredentialVersionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public CredentialVersionFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof GymUserDetails details) {
            CurrentUser sessionUser = details.getCurrentUser();
            Optional<UserAccount> latestOpt = userRepository.findById(sessionUser.id());
            boolean valid = latestOpt.isPresent()
                    && latestOpt.get().active()
                    && latestOpt.get().credentialVersion() == sessionUser.credentialVersion()
                    && latestOpt.get().role() == sessionUser.role()
                    && Objects.equals(latestOpt.get().trainerId(), sessionUser.trainerId());
            if (!valid) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login?invalidated");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
