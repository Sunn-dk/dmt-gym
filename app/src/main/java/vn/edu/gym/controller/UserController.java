package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.UserCreateInput;
import vn.edu.gym.service.UserService;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("userForm")) {
            model.addAttribute("userForm", new UserCreateInput());
        }
        populateModel(model);
        return "users/index";
    }

    @PostMapping
    public String create(
            @ModelAttribute("userForm") UserCreateInput form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            form.setPassword("");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", ControllerParamUtils.describeBindingErrors(bindingResult));
            model.addAttribute("createRawInputs", ControllerParamUtils.extractSafeInputs(request));
            populateModel(model);
            return "users/index";
        }
        try {
            var created = userService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo tài khoản '" + created.username() + "' (" + created.role().getLabel() + ").");
            return "redirect:/users";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            // Never reflect raw passwords back on error
            form.setPassword("");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("createRawInputs", ControllerParamUtils.extractSafeInputs(request));
            populateModel(model);
            return "users/index";
        }
    }

    @PostMapping("/{id}/name")
    public String updateName(
            @PathVariable long id,
            @RequestParam(required = false) String fullName,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.updateName(id, fullName);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật tên hiển thị tài khoản #" + id + ".");
            return "redirect:/users";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("editUserId", id);
            model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
            model.addAttribute("userForm", new UserCreateInput());
            populateModel(model);
            return "users/index";
        }
    }

    @PostMapping("/{id}/active")
    public String setActive(
            @PathVariable long id,
            @RequestParam boolean active,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.setActive(id, active);
            redirectAttributes.addFlashAttribute("successMessage",
                    active ? "Đã mở khóa tài khoản #" + id + "."
                            : "Đã khóa tài khoản #" + id + " và thu hồi phiên đăng nhập cũ.");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(
            @PathVariable long id,
            @RequestParam(required = false) String newPassword,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.resetPassword(id, newPassword);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã đặt lại mật khẩu tài khoản #" + id + " và thu hồi các phiên đang đăng nhập.");
            return "redirect:/users";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            // Never echo newPassword back into model/HTML
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("resetPasswordUserId", id);
            model.addAttribute("userForm", new UserCreateInput());
            populateModel(model);
            return "users/index";
        }
    }

    private void populateModel(Model model) {
        model.addAttribute("users", userService.listAll());
        model.addAttribute("availableTrainers", userService.listAvailableTrainersForUserCreation());
        model.addAttribute("roles", Role.values());
    }
}
