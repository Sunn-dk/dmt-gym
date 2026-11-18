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
import vn.edu.gym.model.PlanForm;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.service.PlanService;

@Controller
@RequestMapping("/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("planForm")) {
            model.addAttribute("planForm", new PlanForm());
        }
        model.addAttribute("plans", planService.listAll());
        model.addAttribute("kinds", PlanKind.values());
        return "plans/index";
    }

    @PostMapping
    public String create(
            @ModelAttribute("planForm") PlanForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderCreateError(
                    response, model,
                    ControllerParamUtils.describeBindingErrors(bindingResult),
                    request
            );
        }
        try {
            var created = planService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo gói mới \"" + created.name() + "\".");
            return "redirect:/plans";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderCreateError(response, model, ex.getMessage(), request);
        }
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @ModelAttribute("editPlanForm") PlanForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderEditError(
                    id, response, model,
                    ControllerParamUtils.describeBindingErrors(bindingResult),
                    request
            );
        }
        try {
            var updated = planService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật gói \"" + updated.name() + "\" (hợp đồng đã bán giữ nguyên thông tin cũ).");
            return "redirect:/plans";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderEditError(id, response, model, ex.getMessage(), request);
        }
    }

    @PostMapping("/{id}/active")
    public String setActive(
            @PathVariable long id,
            @RequestParam boolean active,
            RedirectAttributes redirectAttributes
    ) {
        try {
            planService.setActive(id, active);
            redirectAttributes.addFlashAttribute("successMessage",
                    active ? "Đã mở bán lại gói #" + id + "." : "Đã ngừng bán gói #" + id + ".");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/plans";
    }

    private String renderCreateError(
            HttpServletResponse response,
            Model model,
            String message,
            HttpServletRequest request
    ) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", message);
        model.addAttribute("createRawInputs", ControllerParamUtils.extractSafeInputs(request));
        model.addAttribute("plans", planService.listAll());
        model.addAttribute("kinds", PlanKind.values());
        return "plans/index";
    }

    private String renderEditError(
            long id,
            HttpServletResponse response,
            Model model,
            String message,
            HttpServletRequest request
    ) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", message);
        model.addAttribute("editPlanId", id);
        model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
        model.addAttribute("planForm", new PlanForm());
        model.addAttribute("plans", planService.listAll());
        model.addAttribute("kinds", PlanKind.values());
        return "plans/index";
    }
}
