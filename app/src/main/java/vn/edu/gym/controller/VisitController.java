package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.VisitView;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.service.DateRangeUtils;
import vn.edu.gym.service.VisitService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/visits")
public class VisitController {

    private final VisitService visitService;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public VisitController(VisitService visitService, MemberRepository memberRepository, Clock clock) {
        this.visitService = visitService;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @GetMapping
    public String index(
            @RequestParam(name = "memberId", required = false) String memberIdRaw,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "fromDate", required = false) String fromDateRaw,
            @RequestParam(name = "toDate", required = false) String toDateRaw,
            @RequestParam(name = "page", required = false) String pageRaw,
            HttpServletResponse response,
            Model model
    ) {
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("openVisits", visitService.listOpenVisits());
        model.addAttribute("membersForSelect", memberRepository.findAllBasicForSelect());
        model.addAttribute("q", keyword == null ? "" : keyword);

        List<String> parseErrors = new ArrayList<>();
        Long parsedMemberId = ControllerParamUtils.parseOptionalLong(memberIdRaw, "Hội viên", parseErrors);
        LocalDate parsedFrom = ControllerParamUtils.parseOptionalDate(fromDateRaw, "Từ ngày", parseErrors);
        LocalDate parsedTo = ControllerParamUtils.parseOptionalDate(toDateRaw, "Đến ngày", parseErrors);
        int parsedPage = ControllerParamUtils.parsePage(pageRaw, parseErrors);

        LocalDate defaultFrom = today.minusDays(VisitService.DEFAULT_HISTORY_LOOKBACK_DAYS);
        boolean bothOmitted = (fromDateRaw == null || fromDateRaw.isBlank())
                && (toDateRaw == null || toDateRaw.isBlank());

        model.addAttribute("selectedMemberId", parsedMemberId);
        model.addAttribute("fromDate", bothOmitted ? defaultFrom : parsedFrom);
        model.addAttribute("toDate", bothOmitted ? today : parsedTo);
        model.addAttribute("fromDateValue", bothOmitted ? defaultFrom.toString() : (fromDateRaw != null ? fromDateRaw : ""));
        model.addAttribute("toDateValue", bothOmitted ? today.toString() : (toDateRaw != null ? toDateRaw : ""));

        if (!parseErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", String.join(" ", parseErrors));
            model.addAttribute("history", new PageResult<VisitView>(List.of(), 0, VisitService.PAGE_SIZE, 0));
            return "visits/index";
        }

        try {
            DateRangeUtils.DateRange range = DateRangeUtils.resolveBoundedRange(
                    parsedFrom, parsedTo, defaultFrom, today, DateRangeUtils.MAX_FILTER_DAYS
            );
            PageResult<VisitView> history = visitService.search(
                    parsedMemberId, keyword, range.fromDate(), range.toDate(), parsedPage
            );
            model.addAttribute("history", history);
        } catch (GymException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("history", new PageResult<VisitView>(List.of(), 0, VisitService.PAGE_SIZE, 0));
        }
        return "visits/index";
    }

    @PostMapping("/check-in")
    public String checkIn(
            @RequestParam(name = "memberId", required = false) String memberIdRaw,
            RedirectAttributes redirectAttributes
    ) {
        List<String> parseErrors = new ArrayList<>();
        Long memberId = ControllerParamUtils.parseOptionalLong(memberIdRaw, "Hội viên", parseErrors);
        if (!parseErrors.isEmpty() || memberId == null) {
            redirectAttributes.addFlashAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            redirectAttributes.addFlashAttribute("errorMessage",
                    parseErrors.isEmpty() ? "Vui lòng chọn hội viên để check-in." : String.join(" ", parseErrors));
            return "redirect:/visits";
        }
        try {
            VisitView visit = visitService.checkIn(memberId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận check-in cho " + visit.memberCode() + " · " + visit.memberName()
                            + " (lượt #" + visit.id() + ").");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("selectedMemberId", memberId);
        }
        return "redirect:/visits";
    }

    @PostMapping("/{id}/check-out")
    public String checkOut(
            @PathVariable long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            VisitView visit = visitService.checkOut(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận check-out lượt #" + visit.id() + " (" + visit.memberCode() + " · " + visit.memberName() + ").");
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            redirectAttributes.addFlashAttribute("errorCode", ex.getCode().name());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/visits";
    }
}
