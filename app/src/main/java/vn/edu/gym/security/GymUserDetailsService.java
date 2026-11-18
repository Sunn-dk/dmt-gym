package vn.edu.gym.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.gym.model.UserAccount;
import vn.edu.gym.repository.UserRepository;

@Service
public class GymUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public GymUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim();
        UserAccount user = userRepository.findByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ"));
        return new GymUserDetails(user);
    }
}
