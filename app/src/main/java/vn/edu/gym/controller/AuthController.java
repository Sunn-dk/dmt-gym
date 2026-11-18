package vn.edu.gym.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.gym.model.CurrentUser;
import vn.edu.gym.model.Role;
import vn.edu.gym.security.SecurityUtils;

@Controller
public class AuthController {

    @GetMapping("/")
    public String home() {
        return SecurityUtils.currentUser()
                .map(this::landingFor)
                .orElse("redirect:/login");
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String invalidated,
            Model model
    ) {
        var existing = SecurityUtils.currentUser();
        if (existing.isPresent()) {
            return landingFor(existing.get());
        }
        if (error != null) {
            model.addAttribute("errorMessage", "Thông tin đăng nhập không hợp lệ.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất khỏi hệ thống.");
        }
        if (invalidated != null) {
            model.addAttribute("errorMessage", "Phiên làm việc đã bị thu hồi hoặc tài khoản đã bị khóa; vui lòng đăng nhập lại.");
        }
        return "login";
    }

    @RequestMapping("/access-denied")
    public String accessDenied(Model model) {
        if (!model.containsAttribute("errorMessage")) {
            model.addAttribute("errorCode", "FORBIDDEN");
            model.addAttribute("errorMessage", "Bạn không có quyền truy cập chức năng hoặc bản ghi này.");
        }
        return "error/403";
    }

    private String landingFor(CurrentUser user) {
        return user.role() == Role.TRAINER ? "redirect:/pt-bookings" : "redirect:/members";
    }
}
