package vn.edu.gym.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.ExpiringMemberView;
import vn.edu.gym.model.ReportSummary;
import vn.edu.gym.model.Role;
import vn.edu.gym.repository.ReportRepository;
import vn.edu.gym.security.SecurityUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {

    public static final int MAX_RANGE_DAYS = DateRangeUtils.MAX_FILTER_DAYS;
    public static final int EXPIRING_WINDOW_DAYS = 7;

    private final ReportRepository reportRepository;
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public ReportSummary summarize(LocalDate fromDate, LocalDate toDate) {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        LocalDate today = LocalDate.now(clock);
        DateRangeUtils.DateRange range = DateRangeUtils.resolveBoundedRange(
                fromDate, toDate, today, today, MAX_RANGE_DAYS
        );
        return reportRepository.summarize(range.fromDate(), range.toDate());
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<ExpiringMemberView> expiringGym() {
        SecurityUtils.requireAnyRole(SecurityUtils.requireCurrentUser(), Role.MANAGER);
        LocalDate today = LocalDate.now(clock);
        return reportRepository.findExpiringGymMembers(today, EXPIRING_WINDOW_DAYS);
    }
}
