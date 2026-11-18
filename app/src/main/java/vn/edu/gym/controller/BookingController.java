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
import vn.edu.gym.model.BookingInput;
import vn.edu.gym.model.MembershipDetailView;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.Role;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.security.SecurityUtils;
import vn.edu.gym.service.AvailabilityService;
import vn.edu.gym.service.BookingService;
import vn.edu.gym.service.TrainerService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/pt-bookings")
public class BookingController {

    private final BookingService bookingService;
    private final TrainerService trainerService;
    private final AvailabilityService availabilityService;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public BookingController(
            BookingService bookingService,
            TrainerService trainerService,
            AvailabilityService availabilityService,
            MemberRepository memberRepository,
            Clock clock
    ) {
        this.bookingService = bookingService;
        this.trainerService = trainerService;
        this.availabilityService = availabilityService;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @GetMapping
    public String index(
            @RequestParam(name = "date", required = false) String dateRaw,
            @RequestParam(name = "trainerId", required = false) String trainerIdRaw,
            @RequestParam(name = "memberId", required = false) String memberIdRaw,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(name = "formMemberId", required = false) String formMemberIdRaw,
            @RequestParam(name = "page", required = false) String pageRaw,
            HttpServletResponse response,
            Model model
    ) {
        List<String> parseErrors = new ArrayList<>();
        LocalDate parsedDate = ControllerParamUtils.parseOptionalDate(dateRaw, "Ngày tập", parseErrors);
        Long parsedTrainerId = ControllerParamUtils.parseOptionalLong(trainerIdRaw, "Huấn luyện viên", parseErrors);
        Long parsedMemberId = ControllerParamUtils.parseOptionalLong(memberIdRaw, "Hội viên", parseErrors);
        Long parsedFormMemberId = ControllerParamUtils.parseOptionalLong(formMemberIdRaw, "Hội viên đặt lịch", parseErrors);
        int parsedPage = ControllerParamUtils.parsePage(pageRaw, parseErrors);

        if (!parseErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", String.join(" ", parseErrors));
            populateModel(null, null, null, "ALL", parsedFormMemberId, 0, null, true, model);
            return "pt-bookings/index";
        }
        populateModel(parsedDate, parsedTrainerId, parsedMemberId, status, parsedFormMemberId, parsedPage, null, false, model);
        return "pt-bookings/index";
    }

    @PostMapping
    public String create(
            @ModelAttribute("bookingForm") BookingInput form,
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
            model.addAttribute("bookingRawInputs", ControllerParamUtils.extractSafeInputs(request));
            populateModel(null, null, null, "ALL", form.getMemberId(), 0, form, false, model);
            return "pt-bookings/index";
        }
        try {
            var created = bookingService.book(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã đặt lịch PT " + created.bookingCode() + " cho " + created.memberCode()
                            + " với " + created.trainerName() + ".");
            return "redirect:/pt-bookings";
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("bookingRawInputs", ControllerParamUtils.extractSafeInputs(request));
            populateModel(null, null, null, "ALL", form.getMemberId(), 0, form, false, model);
            return "pt-bookings/index";
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancel(
            @PathVariable long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var updated = bookingService.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã hủy lịch " + updated.bookingCode() + " và giải phóng 1 quyền giữ buổi.");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/pt-bookings";
    }

    @PostMapping("/{id}/complete")
    public String complete(
            @PathVariable long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var updated = bookingService.complete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã chốt Hoàn thành buổi " + updated.bookingCode() + " (tính 1 buổi đã dùng).");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/pt-bookings";
    }

    @PostMapping("/{id}/no-show")
    public String markNoShow(
            @PathVariable long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var updated = bookingService.markNoShow(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã ghi nhận Vắng mặt buổi " + updated.bookingCode() + " (giải phóng quyền giữ, không tiêu buổi).");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/pt-bookings";
    }

    private void populateModel(
            LocalDate date,
            Long trainerId,
            Long memberId,
            String status,
            Long formMemberId,
            int page,
            BookingInput preservedForm,
            boolean skipSearchOnParseError,
            Model model
    ) {
        var actor = SecurityUtils.requireCurrentUser();
        boolean isTrainer = actor.role() == Role.TRAINER;

        model.addAttribute("isTrainerRole", isTrainer);
        model.addAttribute("overduePending", bookingService.listOverduePending());
        model.addAttribute("pageResult", skipSearchOnParseError
                ? new PageResult<>(List.of(), 0, BookingService.PAGE_SIZE, 0)
                : bookingService.search(date, trainerId, memberId, status, page));
        model.addAttribute("filterDate", date);
        model.addAttribute("filterTrainerId", isTrainer ? actor.trainerId() : trainerId);
        model.addAttribute("filterMemberId", memberId);
        model.addAttribute("filterStatus", status);
        model.addAttribute("trainers", trainerService.listForCurrentActor());

        if (!isTrainer) {
            var allMembers = memberRepository.findAllBasicForSelect();
            var activeMembers = memberRepository.findAllActiveForSelect();
            model.addAttribute("allMembers", allMembers);
            model.addAttribute("activeMembers", activeMembers);
            model.addAttribute("activeTrainers", trainerService.listAllActive());
            model.addAttribute("availabilitySlots", availabilityService.listForCurrentActor(null, date));

            Long selectedFormMemberId = formMemberId;
            if (selectedFormMemberId == null && !activeMembers.isEmpty()) {
                selectedFormMemberId = activeMembers.get(0).id();
            }
            List<MembershipDetailView> ptContracts = selectedFormMemberId != null
                    ? bookingService.listPtContractsForMember(selectedFormMemberId)
                    : List.of();
            model.addAttribute("formMemberId", selectedFormMemberId);
            model.addAttribute("ptContracts", ptContracts);

            BookingInput form = preservedForm;
            if (form == null) {
                form = new BookingInput();
                form.setMemberId(selectedFormMemberId);
                form.setBookingDate(LocalDate.now(clock).plusDays(1));
                form.setStartTime(LocalTime.of(9, 0));
                if (!ptContracts.isEmpty()) {
                    form.setMembershipId(ptContracts.get(0).membership().id());
                }
            }
            model.addAttribute("bookingForm", form);
        }
    }
}
