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
import vn.edu.gym.model.EquipmentForm;
import vn.edu.gym.model.EquipmentStatus;
import vn.edu.gym.service.EquipmentService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public String index(
            @RequestParam(name = "q", required = false) String keyword,
            Model model
    ) {
        if (!model.containsAttribute("equipmentForm")) {
            model.addAttribute("equipmentForm", new EquipmentForm());
        }
        model.addAttribute("q", keyword == null ? "" : keyword);
        model.addAttribute("items", equipmentService.listAll(keyword));
        return "equipment/index";
    }

    @PostMapping
    public String create(
            @ModelAttribute("equipmentForm") EquipmentForm form,
            BindingResult bindingResult,
            @RequestParam(name = "q", required = false) String keyword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderError(response, model, keyword, null, null,
                    ControllerParamUtils.describeBindingErrors(bindingResult), request, false);
        }
        try {
            var created = equipmentService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm thiết bị " + created.code() + " · " + created.name() + ".");
            return redirectWithQuery(keyword);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderError(response, model, keyword, null, null, ex.getMessage(), request, false);
        }
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @ModelAttribute("editEquipmentForm") EquipmentForm form,
            BindingResult bindingResult,
            @RequestParam(name = "q", required = false) String keyword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderError(response, model, keyword, id, null,
                    ControllerParamUtils.describeBindingErrors(bindingResult), request, true);
        }
        try {
            var updated = equipmentService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật thiết bị " + updated.code() + ".");
            return redirectWithQuery(keyword);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderError(response, model, keyword, id, null, ex.getMessage(), request, true);
        }
    }

    @PostMapping("/{id}/report-broken")
    public String reportBroken(
            @PathVariable long id,
            @RequestParam(required = false) String note,
            @RequestParam(name = "q", required = false) String keyword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var updated = equipmentService.reportBroken(id, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã báo hỏng thiết bị " + updated.code() + ".");
            return redirectWithQuery(keyword);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderError(response, model, keyword, null, id, ex.getMessage(), request, true);
        }
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable long id,
            @RequestParam EquipmentStatus targetStatus,
            @RequestParam(required = false) String note,
            @RequestParam(name = "q", required = false) String keyword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var updated = equipmentService.changeStatus(id, targetStatus, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã chuyển thiết bị " + updated.code() + " sang " + updated.status().getLabel() + ".");
            return redirectWithQuery(keyword);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderError(response, model, keyword, null, id, ex.getMessage(), request, true);
        }
    }

    private String redirectWithQuery(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "redirect:/equipment";
        }
        return "redirect:/equipment?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    private String renderError(
            HttpServletResponse response,
            Model model,
            String keyword,
            Long editEquipmentId,
            Long statusNoteEquipmentId,
            String message,
            HttpServletRequest request,
            boolean resetCreateForm
    ) {
        String effectiveQ = keyword == null ? "" : keyword;
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", message);
        model.addAttribute("q", effectiveQ);
        model.addAttribute("items", equipmentService.listAll(effectiveQ));
        if (editEquipmentId != null) {
            model.addAttribute("editEquipmentId", editEquipmentId);
            model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
        }
        if (statusNoteEquipmentId != null) {
            model.addAttribute("statusNoteEquipmentId", statusNoteEquipmentId);
            model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
        }
        if (resetCreateForm) {
            model.addAttribute("equipmentForm", new EquipmentForm());
        } else {
            model.addAttribute("createRawInputs", ControllerParamUtils.extractSafeInputs(request));
        }
        return "equipment/index";
    }
}
