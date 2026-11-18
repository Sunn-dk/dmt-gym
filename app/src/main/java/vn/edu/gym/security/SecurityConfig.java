package vn.edu.gym.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import vn.edu.gym.model.Role;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CredentialVersionFilter credentialVersionFilter;

    public SecurityConfig(CredentialVersionFilter credentialVersionFilter) {
        this.credentialVersionFilter = credentialVersionFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/favicon.ico", "/favicon.svg", "/login", "/error", "/access-denied").permitAll()
                        .requestMatchers("/users/**", "/reports/**").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/members/*/active", "/plans", "/plans/**").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/trainers", "/trainers/*", "/trainers/*/active").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/trainers/*/availability", "/availability/**").hasAnyRole("MANAGER", "TRAINER")
                        .requestMatchers(HttpMethod.POST, "/pt-bookings", "/pt-bookings/*/cancel").hasAnyRole("MANAGER", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/pt-bookings/*/complete", "/pt-bookings/*/no-show").hasAnyRole("MANAGER", "TRAINER")
                        .requestMatchers(HttpMethod.POST, "/equipment", "/equipment/*", "/equipment/*/status").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/equipment/*/report-broken").hasAnyRole("MANAGER", "RECEPTIONIST", "TRAINER")
                        .requestMatchers("/members/**", "/plans/**", "/sales/**", "/receipts/**", "/visits/**")
                        .hasAnyRole("MANAGER", "RECEPTIONIST")
                        .requestMatchers("/trainers/**", "/availability/**", "/pt-bookings/**", "/equipment/**")
                        .hasAnyRole("MANAGER", "RECEPTIONIST", "TRAINER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(authenticationSuccessHandler())
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterAfter(credentialVersionFilter, SecurityContextHolderFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof GymUserDetails details
                    && details.getCurrentUser().role() == Role.TRAINER) {
                response.sendRedirect(request.getContextPath() + "/pt-bookings");
            } else {
                response.sendRedirect(request.getContextPath() + "/members");
            }
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.getRequestDispatcher("/access-denied").forward(request, response);
        };
    }
}
