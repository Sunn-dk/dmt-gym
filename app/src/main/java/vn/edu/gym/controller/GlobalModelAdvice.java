package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.security.SecurityUtils;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@ControllerAdvice
public class GlobalModelAdvice {

    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");
    private final Clock clock;

    public GlobalModelAdvice(Clock clock) {
        this.clock = clock;
    }

    @ModelAttribute("currentUser")
    public CurrentUser currentUser() {
        return SecurityUtils.currentUser().orElse(null);
    }

    @ModelAttribute("serverNow")
    public LocalDateTime serverNow() {
        return LocalDateTime.now(clock);
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    public static String formatVnd(long amount) {
        NumberFormat nf = NumberFormat.getIntegerInstance(VI_LOCALE);
        return nf.format(amount) + " đ";
    }
}
