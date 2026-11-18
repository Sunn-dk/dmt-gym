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
import vn.edu.gym.model.MemberForm;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.service.MemberService;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public String list(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "page", required = false) String pageRaw,
            HttpServletResponse response,
            Model model
    ) {
        if (!model.containsAttribute("memberForm")) {
            model.addAttribute("memberForm", new MemberForm());
        }
        String effectiveQ = keyword == null ? "" : keyword;
        model.addAttribute("q", effectiveQ);

        List<String> parseErrors = new ArrayList<>();
        int page = ControllerParamUtils.parsePage(pageRaw, parseErrors);
        if (!parseErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", String.join(" ", parseErrors));
            model.addAttribute("pageResult", new PageResult<>(List.of(), 0, MemberService.PAGE_SIZE, 0));
            return "members/index";
        }
        model.addAttribute("pageResult", memberService.search(keyword, page));
        return "members/index";
    }

    @PostMapping
    public String create(
            @ModelAttribute("memberForm") MemberForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderCreateError(response, model,
                    ControllerParamUtils.describeBindingErrors(bindingResult), request);
        }
        try {
            var created = memberService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo hồ sơ hội viên " + created.memberCode() + " · " + created.fullName() + ".");
            return "redirect:/members/" + created.id();
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderCreateError(response, model, ex.getMessage(), request);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        var detail = memberService.getDetail(id);
        model.addAttribute("detail", detail);
        if (!model.containsAttribute("memberForm")) {
            model.addAttribute("memberForm", new MemberForm(
                    detail.member().fullName(),
                    detail.member().phone(),
                    detail.member().birthDate()
            ));
        }
        return "members/detail";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @ModelAttribute("memberForm") MemberForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderUpdateError(id, response, model,
                    ControllerParamUtils.describeBindingErrors(bindingResult), request);
        }
        try {
            var updated = memberService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật hồ sơ hội viên " + updated.memberCode() + ".");
            return "redirect:/members/" + id;
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderUpdateError(id, response, model, ex.getMessage(), request);
        }
    }

    @PostMapping("/{id}/active")
    public String setActive(
            @PathVariable long id,
            @RequestParam boolean active,
            RedirectAttributes redirectAttributes
    ) {
        try {
            memberService.setActive(id, active);
            redirectAttributes.addFlashAttribute("successMessage",
                    active ? "Đã cho phép phục vụ lại hội viên HV-" + id + "."
                            : "Đã ngừng phục vụ hội viên HV-" + id + ".");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            if (!ex.getDetails().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorDetails", ex.getDetails());
            }
        }
        return "redirect:/members/" + id;
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
        model.addAttribute("q", "");
        model.addAttribute("pageResult", memberService.search("", 0));
        return "members/index";
    }

    private String renderUpdateError(
            long id,
            HttpServletResponse response,
            Model model,
            String message,
            HttpServletRequest request
    ) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", message);
        model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
        model.addAttribute("detail", memberService.getDetail(id));
        return "members/detail";
    }
}
