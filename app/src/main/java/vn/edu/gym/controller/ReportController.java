package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.ReportSummary;
import vn.edu.gym.service.DateRangeUtils;
import vn.edu.gym.service.ReportService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final Clock clock;

    public ReportController(ReportService reportService, Clock clock) {
        this.reportService = reportService;
        this.clock = clock;
    }

    @GetMapping
    public String index(
            @RequestParam(name = "fromDate", required = false) String fromDateRaw,
            @RequestParam(name = "toDate", required = false) String toDateRaw,
            HttpServletResponse response,
            Model model
    ) {
        LocalDate today = LocalDate.now(clock);
        List<String> parseErrors = new ArrayList<>();
        LocalDate parsedFrom = ControllerParamUtils.parseOptionalDate(fromDateRaw, "Từ ngày", parseErrors);
        LocalDate parsedTo = ControllerParamUtils.parseOptionalDate(toDateRaw, "Đến ngày", parseErrors);

        model.addAttribute("today", today);
        model.addAttribute("fromDate", parsedFrom != null ? parsedFrom : today);
        model.addAttribute("toDate", parsedTo != null ? parsedTo : today);
        model.addAttribute("fromDateValue",
                fromDateRaw != null ? fromDateRaw : (parsedFrom == null && parsedTo == null ? today.toString() : ""));
        model.addAttribute("toDateValue",
                toDateRaw != null ? toDateRaw : (parsedFrom == null && parsedTo == null ? today.toString() : ""));
        model.addAttribute("expiringMembers", reportService.expiringGym());

        if (!parseErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", String.join(" ", parseErrors));
            model.addAttribute("summary", null);
            return "reports/index";
        }

        try {
            DateRangeUtils.DateRange range = DateRangeUtils.resolveBoundedRange(
                    parsedFrom, parsedTo, today, today, ReportService.MAX_RANGE_DAYS
            );
            ReportSummary summary = reportService.summarize(range.fromDate(), range.toDate());
            model.addAttribute("summary", summary);
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("summary", null);
        }
        return "reports/index";
    }
}
