package vn.edu.gym.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.UserAccount;

import java.util.Collection;
import java.util.List;

public class GymUserDetails implements UserDetails {

    private final CurrentUser currentUser;
    private final String passwordHash;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public GymUserDetails(UserAccount user) {
        this.currentUser = new CurrentUser(
                user.id(),
                user.username(),
                user.fullName(),
                user.role(),
                user.trainerId(),
                user.credentialVersion()
        );
        this.passwordHash = user.passwordHash();
        this.active = user.active();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    }

    public CurrentUser getCurrentUser() {
        return currentUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return currentUser.username();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
