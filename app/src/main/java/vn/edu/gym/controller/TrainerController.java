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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.AvailabilityForm;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.TrainerForm;
import vn.edu.gym.security.SecurityUtils;
import vn.edu.gym.service.AvailabilityService;
import vn.edu.gym.service.TrainerService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
public class TrainerController {

    private final TrainerService trainerService;
    private final AvailabilityService availabilityService;
    private final Clock clock;

    public TrainerController(
            TrainerService trainerService,
            AvailabilityService availabilityService,
            Clock clock
    ) {
        this.trainerService = trainerService;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    @GetMapping("/trainers")
    public String index(
            @RequestParam(required = false) String trainerId,
            @RequestParam(required = false) String date,
            HttpServletResponse response,
            Model model
    ) {
        List<String> parseErrors = new ArrayList<>();
        Long parsedTrainerId = ControllerParamUtils.parseOptionalLong(trainerId, "Huấn luyện viên", parseErrors);
        LocalDate parsedDate = ControllerParamUtils.parseOptionalDate(date, "Ngày lọc", parseErrors);
        if (!parseErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", String.join(" ", parseErrors));
        }
        populateModel(parsedTrainerId, parsedDate, model);
        return "trainers/index";
    }

    @GetMapping("/trainers/{id}/availability")
    public String trainerAvailability(
            @PathVariable long id,
            @RequestParam(required = false) String date,
            HttpServletResponse response,
            Model model
    ) {
        List<String> parseErrors = new ArrayList<>();
        LocalDate parsedDate = ControllerParamUtils.parseOptionalDate(date, "Ngày lọc", parseErrors);
        if (!parseErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", String.join(" ", parseErrors));
        }
        populateModel(id, parsedDate, model);
        return "trainers/index";
    }

    @PostMapping("/trainers")
    public String createTrainer(
            @ModelAttribute("trainerForm") TrainerForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", ControllerParamUtils.describeBindingErrors(bindingResult));
            model.addAttribute("createTrainerRaw", ControllerParamUtils.extractSafeInputs(request));
            populateModel(null, null, model);
            return "trainers/index";
        }
        try {
            var created = trainerService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm hồ sơ PT \"" + created.fullName() + "\".");
            return "redirect:/trainers";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("createTrainerRaw", ControllerParamUtils.extractSafeInputs(request));
            populateModel(null, null, model);
            return "trainers/index";
        }
    }

    @PostMapping("/trainers/{id}")
    public String updateTrainer(
            @PathVariable long id,
            @ModelAttribute("editTrainerForm") TrainerForm form,
            BindingResult bindingResult,
            @RequestParam(required = false) String filterTrainerId,
            @RequestParam(required = false) String filterDate,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderEditTrainerError(id, filterTrainerId, filterDate, response, model,
                    ControllerParamUtils.describeBindingErrors(bindingResult), request);
        }
        try {
            var updated = trainerService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật hồ sơ PT \"" + updated.fullName() + "\".");
            return redirectToTrainers(filterTrainerId, filterDate);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderEditTrainerError(id, filterTrainerId, filterDate, response, model, ex.getMessage(), request);
        }
    }

    @PostMapping("/trainers/{id}/active")
    public String setTrainerActive(
            @PathVariable long id,
            @RequestParam boolean active,
            RedirectAttributes redirectAttributes
    ) {
        try {
            trainerService.setActive(id, active);
            redirectAttributes.addFlashAttribute("successMessage",
                    active ? "Đã bật nhận lịch lại cho PT #" + id + "."
                            : "Đã ngừng nhận lịch cho PT #" + id + ".");
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
        return "redirect:/trainers";
    }

    @PostMapping("/trainers/{id}/availability")
    public String createAvailability(
            @PathVariable long id,
            @ModelAttribute("availabilityForm") AvailabilityForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            form.setTrainerId(id);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", ControllerParamUtils.describeBindingErrors(bindingResult));
            model.addAttribute("createAvailRaw", ControllerParamUtils.extractSafeInputs(request));
            populateModel(id, form.getDate(), model);
            return "trainers/index";
        }
        try {
            var created = availabilityService.create(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã khai báo khung giờ rảnh cho " + created.trainerName() + " (" + created.startAt().toLocalDate() + ").");
            return "redirect:/trainers?trainerId=" + id + "&date=" + created.startAt().toLocalDate();
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            form.setTrainerId(id);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("createAvailRaw", ControllerParamUtils.extractSafeInputs(request));
            populateModel(id, form.getDate(), model);
            return "trainers/index";
        }
    }

    @PostMapping("/availability/{id}")
    public String updateAvailability(
            @PathVariable long id,
            @ModelAttribute("editAvailabilityForm") AvailabilityForm form,
            BindingResult bindingResult,
            @RequestParam(required = false) String filterTrainerId,
            @RequestParam(required = false) String filterDate,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderEditSlotError(id, filterTrainerId, filterDate, response, model,
                    ControllerParamUtils.describeBindingErrors(bindingResult), List.of(), request);
        }
        try {
            var updated = availabilityService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật khung giờ rảnh #" + updated.id() + ".");
            return redirectToTrainers(filterTrainerId, filterDate);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            return renderEditSlotError(id, filterTrainerId, filterDate, response, model,
                    ex.getMessage(), ex.getDetails(), request);
        }
    }

    @PostMapping("/availability/{id}/delete")
    public String deleteAvailability(
            @PathVariable long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            availabilityService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khung giờ rảnh #" + id + ".");
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
        return "redirect:/trainers";
    }

    private String renderEditTrainerError(
            long id,
            String filterTrainerId,
            String filterDate,
            HttpServletResponse response,
            Model model,
            String message,
            HttpServletRequest request
    ) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", message);
        model.addAttribute("editTrainerId", id);
        model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
        List<String> ignored = new ArrayList<>();
        populateModel(
                ControllerParamUtils.parseOptionalLong(filterTrainerId, "PT", ignored),
                ControllerParamUtils.parseOptionalDate(filterDate, "Ngày", ignored),
                model
        );
        return "trainers/index";
    }

    private String renderEditSlotError(
            long id,
            String filterTrainerId,
            String filterDate,
            HttpServletResponse response,
            Model model,
            String message,
            List<String> details,
            HttpServletRequest request
    ) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", message);
        if (details != null && !details.isEmpty()) {
            model.addAttribute("errorDetails", details);
        }
        model.addAttribute("editSlotId", id);
        model.addAttribute("editRawInputs", ControllerParamUtils.extractSafeInputs(request));
        List<String> ignored = new ArrayList<>();
        populateModel(
                ControllerParamUtils.parseOptionalLong(filterTrainerId, "PT", ignored),
                ControllerParamUtils.parseOptionalDate(filterDate, "Ngày", ignored),
                model
        );
        return "trainers/index";
    }

    private String redirectToTrainers(String filterTrainerId, String filterDate) {
        List<String> qs = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        Long parsedTrainer = ControllerParamUtils.parseOptionalLong(filterTrainerId, "PT", ignored);
        LocalDate parsedDate = ControllerParamUtils.parseOptionalDate(filterDate, "Ngày", ignored);
        if (parsedTrainer != null) {
            qs.add("trainerId=" + parsedTrainer);
        }
        if (parsedDate != null) {
            qs.add("date=" + parsedDate);
        }
        return qs.isEmpty() ? "redirect:/trainers" : "redirect:/trainers?" + String.join("&", qs);
    }

    private void populateModel(Long trainerId, LocalDate date, Model model) {
        var actor = SecurityUtils.requireCurrentUser();
        var trainers = trainerService.listForCurrentActor();
        Long effectiveTrainerId = actor.role() == Role.TRAINER ? actor.trainerId() : trainerId;
        var slots = availabilityService.listForCurrentActor(effectiveTrainerId, date);

        model.addAttribute("trainers", trainers);
        model.addAttribute("slots", slots);
        model.addAttribute("filterTrainerId", effectiveTrainerId);
        model.addAttribute("filterDate", date);

        if (!model.containsAttribute("trainerForm")) {
            model.addAttribute("trainerForm", new TrainerForm());
        }
        if (!model.containsAttribute("availabilityForm")) {
            AvailabilityForm af = new AvailabilityForm();
            af.setDate(date != null ? date : LocalDate.now(clock).plusDays(1));
            af.setTrainerId(effectiveTrainerId != null ? effectiveTrainerId : (!trainers.isEmpty() ? trainers.get(0).id() : null));
            model.addAttribute("availabilityForm", af);
        }
    }
}
