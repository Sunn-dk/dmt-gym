package vn.edu.gym;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.gym.config.ClockConfig;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.AvailabilityForm;
import vn.edu.gym.model.BookingInput;
import vn.edu.gym.model.BookingStatus;
import vn.edu.gym.model.EquipmentForm;
import vn.edu.gym.model.EquipmentStatus;
import vn.edu.gym.model.MemberForm;
import vn.edu.gym.model.MembershipStatus;
import vn.edu.gym.model.PlanForm;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.SaleCommand;
import vn.edu.gym.model.SaleQuote;
import vn.edu.gym.model.SaleType;
import vn.edu.gym.model.TrainerForm;
import vn.edu.gym.model.UserAccount;
import vn.edu.gym.model.UserCreateInput;
import vn.edu.gym.repository.BookingRepository;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.repository.PaymentRepository;
import vn.edu.gym.repository.UserRepository;
import vn.edu.gym.repository.VisitRepository;
import vn.edu.gym.security.GymUserDetails;
import vn.edu.gym.service.AvailabilityService;
import vn.edu.gym.service.BookingService;
import vn.edu.gym.service.EquipmentService;
import vn.edu.gym.service.MemberService;
import vn.edu.gym.service.PlanService;
import vn.edu.gym.service.ReportService;
import vn.edu.gym.service.SaleService;
import vn.edu.gym.service.TrainerService;
import vn.edu.gym.service.UserService;
import vn.edu.gym.service.VisitService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Full integration & concurrency test suite running against real MySQL 8 (gym_exam_test).
 * Covers TC-03..TC-42 business, transaction, rollback, snapshot, quota, and 2-connection lock tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class GymMySqlIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MutableClock clock;

    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserService userService;
    @Autowired private MemberService memberService;
    @Autowired private PlanService planService;
    @Autowired private SaleService saleService;
    @Autowired private VisitService visitService;
    @Autowired private TrainerService trainerService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private BookingService bookingService;
    @Autowired private ReportService reportService;
    @Autowired private EquipmentService equipmentService;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (dbName == null || !dbName.toLowerCase().contains("test")) {
            throw new IllegalStateException("Refusing to reset non-test database: " + dbName);
        }
        String[] dropOrder = {
                "visits", "payments", "equipment", "pt_bookings", "trainer_availability",
                "memberships", "plans", "members", "users", "trainers"
        };
        for (String table : dropOrder) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
        }
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema.sql"));
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/seed_trainers.sql"));
        }
        String hash = passwordEncoder.encode("Gym@12345");
        String userInsert = "INSERT INTO users (id, username, password_hash, full_name, role, trainer_id, credential_version, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0, TRUE)";
        jdbcTemplate.update(userInsert, 1L, "manager", hash, "Quản lý mẫu", "MANAGER", null);
        jdbcTemplate.update(userInsert, 2L, "reception", hash, "Lễ tân mẫu", "RECEPTIONIST", null);
        jdbcTemplate.update(userInsert, 3L, "ptminh", hash, "PT mẫu Minh", "TRAINER", 1L);
        jdbcTemplate.update(userInsert, 4L, "ptnam", hash, "PT mẫu Nam", "TRAINER", 2L);

        try (Connection conn = dataSource.getConnection()) {
            String rawSeed = new String(new ClassPathResource("db/seed.sql").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String anchor = "SET @d = CURRENT_DATE;";
            int firstIdx = rawSeed.indexOf(anchor);
            assertTrue(firstIdx >= 0 && rawSeed.indexOf(anchor, firstIdx + 1) < 0,
                    "db/seed.sql must contain exactly one 'SET @d = CURRENT_DATE;' anchor");
            String fixedSeed = rawSeed
                    .replace(anchor, "SET @d = '2026-11-17';")
                    .replace("NOW()", "'2026-11-17 10:00:00'");
            ScriptUtils.executeSqlScript(conn, new org.springframework.core.io.ByteArrayResource(
                    fixedSeed.getBytes(StandardCharsets.UTF_8)));
        }
        clock.setDateTime(LocalDateTime.of(2026, 11, 17, 10, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        reset(paymentRepository);
    }

    private void authenticateAs(String username) {
        UserAccount ua = userRepository.findByUsername(username).orElseThrow();
        GymUserDetails details = new GymUserDetails(ua);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
    }

    private GymException assertGymError(ErrorCode expected, Runnable action) {
        GymException ex = assertThrows(GymException.class, action::run);
        assertEquals(expected, ex.getCode(), "Expected " + expected + " but got " + ex.getCode() + ": " + ex.getMessage());
        return ex;
    }

    @Test
    void verifySqlAnomaliesAndSeedTotals() {
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT m.id FROM memberships m LEFT JOIN payments p ON p.membership_id=m.id WHERE p.id IS NULL").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT p.id FROM payments p JOIN memberships m ON m.id=p.membership_id WHERE p.amount_vnd<>m.price_vnd_snapshot").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT a.id, b.id FROM memberships a JOIN memberships b ON a.member_id=b.member_id AND a.id<b.id "
                        + "AND a.kind_snapshot='GYM' AND b.kind_snapshot='GYM' AND a.start_date<b.end_date AND b.start_date<a.end_date").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT member_id FROM visits WHERE check_out_at IS NULL GROUP BY member_id HAVING COUNT(*)>1").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT b.id FROM pt_bookings b JOIN memberships m ON m.id=b.membership_id "
                        + "WHERE m.kind_snapshot<>'PT' OR m.member_id<>b.member_id OR DATE(b.start_at)<m.start_date OR DATE(b.start_at)>=m.end_date").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT v.id FROM visits v JOIN memberships m ON m.id=v.membership_id WHERE m.kind_snapshot<>'GYM' OR m.member_id<>v.member_id").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT a.id, b.id FROM pt_bookings a JOIN pt_bookings b ON a.id<b.id AND a.status='BOOKED' AND b.status='BOOKED' "
                        + "AND (a.trainer_id=b.trainer_id OR a.member_id=b.member_id) AND a.start_at<b.end_at AND b.start_at<a.end_at").size());
        assertEquals(0, jdbcTemplate.queryForList(
                "SELECT m.id FROM memberships m LEFT JOIN pt_bookings b ON b.membership_id=m.id WHERE m.kind_snapshot='PT' "
                        + "GROUP BY m.id, m.sessions_total_snapshot "
                        + "HAVING SUM(CASE WHEN b.status IN ('BOOKED','COMPLETED') THEN 1 ELSE 0 END) > m.sessions_total_snapshot").size());

        authenticateAs("manager");
        LocalDate today = LocalDate.now(clock);
        var summary = reportService.summarize(today.minusDays(60), today.plusDays(1));
        assertEquals(4_700_000L, summary.totalRevenueVnd());
        assertEquals(3L, summary.totalVisits());
        assertEquals(1L, summary.completedPtSessions());

        var binhPt = bookingService.listPtContractsForMember(2L);
        assertEquals(1, binhPt.size());
        assertEquals(4, binhPt.get(0).quota().total());
        assertEquals(1, binhPt.get(0).quota().used());
        assertEquals(2, binhPt.get(0).quota().reserved());
        assertEquals(1, binhPt.get(0).quota().available());
    }

    @Test
    void tc03_tc04_tc05_memberValidationPaginationAndPendingBookingDeactivationGuard() {
        clock.setDateTime(LocalDateTime.of(2026, 11, 17, 10, 0));
        authenticateAs("reception");

        // Under 18 by 1 day -> rejected; exact 18th birthday -> accepted
        MemberForm underage = new MemberForm("Trần Thiếu Tuổi", "0911111111", LocalDate.of(2008, 11, 18));
        assertGymError(ErrorCode.INVALID_INPUT, () -> memberService.create(underage));

        MemberForm exact18 = new MemberForm("  Nguyễn Đủ Tuổi  ", "0911111111", LocalDate.of(2008, 11, 17));
        var created = memberService.create(exact18);
        assertEquals("Nguyễn Đủ Tuổi", created.fullName());

        // Duplicate phone & bad phone format
        assertGymError(ErrorCode.DUPLICATE_PHONE,
                () -> memberService.create(new MemberForm("Người Trùng", "0911111111", LocalDate.of(2000, 1, 1))));
        assertGymError(ErrorCode.INVALID_INPUT,
                () -> memberService.create(new MemberForm("SĐT Sai", "1234567890", LocalDate.of(2000, 1, 1))));

        // Deactivate Bình (id=2) who has BOOKED -> PENDING_BOOKINGS
        authenticateAs("manager");
        var pendingEx = assertGymError(ErrorCode.PENDING_BOOKINGS, () -> memberService.setActive(2L, false));
        assertFalse(pendingEx.getDetails().isEmpty());

        // Deactivate Hà (id=5) who has NO BOOKED -> allowed even with open visit; check-out still works
        memberService.setActive(5L, false);
        assertFalse(memberService.getDetail(5L).member().active());
        // Hà's seeded open visit is at 2026-11-16 18:00; 2026-11-17 10:00 clock ensures check-out >= check-in
        clock.setDateTime(LocalDateTime.of(2026, 11, 17, 10, 0));
        var closedVisit = visitService.checkOut(1L);
        assertFalse(closedVisit.inside());
        clock.setDateTime(LocalDateTime.of(2026, 11, 17, 10, 0));

        // Create 20 more members to test pagination (20/page)
        for (int i = 0; i < 20; i++) {
            memberService.create(new MemberForm("Hội viên P" + i, String.format("0988000%03d", i), LocalDate.of(1999, 1, 1)));
        }
        var p0 = memberService.search("", 0);
        var p1 = memberService.search("", 1);
        assertEquals(20, p0.items().size());
        assertTrue(p1.items().size() >= 7);
    }

    @Test
    void tc07_tc08_tc09_tc10_tc11_tc15_tc30_salesRollbackIdempotencyAndSnapshot() {
        clock.setDateTime(LocalDateTime.of(2026, 11, 17, 10, 0));
        authenticateAs("reception");

        // An (id=1) has PT contract #8 but no GYM -> can purchase GYM 30d
        SaleQuote quote = saleService.prepareQuote(1L, 1L, SaleType.PURCHASE, null);
        assertEquals(LocalDate.of(2026, 11, 17), quote.expectedStart());
        assertEquals(LocalDate.of(2026, 12, 17), quote.expectedEnd());
        assertEquals(LocalDate.of(2026, 12, 16), quote.lastUsableDate());

        // Amount mismatch & unconfirmed cash rejected
        SaleCommand badAmt = toCommand(quote, 299_999L, true);
        assertGymError(ErrorCode.AMOUNT_MISMATCH, () -> saleService.sell(badAmt));
        SaleCommand unconfirmed = toCommand(quote, 300_000L, false);
        assertGymError(ErrorCode.AMOUNT_MISMATCH, () -> saleService.sell(unconfirmed));

        // TC-10: DB failure after INSERT membership before INSERT payment -> full rollback
        long msBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Long.class);
        long payBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
        doThrow(new DataRetrievalFailureException("Simulated payment DB failure"))
                .when(paymentRepository).insert(anyLong(), anyLong(), any(), anyLong());
        SaleCommand txCmd = toCommand(quote, 300_000L, true);
        assertThrows(DataAccessException.class, () -> saleService.sell(txCmd));
        reset(paymentRepository);
        assertEquals(msBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Long.class));
        assertEquals(payBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class));

        // TC-11: Concurrent duplicate submit with same request_key on 2 connections
        SaleCommand validCmd = toCommand(quote, 300_000L, true);
        var results = runConcurrently(
                () -> {
                    authenticateAs("reception");
                    return saleService.sell(validCmd);
                },
                () -> {
                    authenticateAs("reception");
                    return saleService.sell(validCmd);
                }
        );
        assertEquals(2, results.successes().size(), "Both identical request_key submissions must resolve to the single receipt");
        assertEquals(results.successes().get(0).paymentId(), results.successes().get(1).paymentId());
        assertEquals(msBefore + 1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Long.class));

        // Same request_key with altered payload -> REQUEST_CONFLICT
        SaleCommand alteredCmd = toCommand(quote, 300_000L, true);
        alteredCmd.setPlanId(2L);
        assertGymError(ErrorCode.REQUEST_CONFLICT, () -> saleService.sell(alteredCmd));

        // Purchasing another GYM while already covered -> ALREADY_COVERED
        assertGymError(ErrorCode.ALREADY_COVERED, () -> saleService.prepareQuote(1L, 1L, SaleType.PURCHASE, null));

        // Purchasing another PT contract for An is allowed as a separate contract
        SaleQuote ptQuote = saleService.prepareQuote(1L, 4L, SaleType.PURCHASE, null);
        var ptReceipt = saleService.sell(toCommand(ptQuote, 1_200_000L, true));

        // TC-15 & TC-30: Manager edits plan price/name/sessions; old receipt & snapshot stay unchanged!
        authenticateAs("manager");
        PlanForm editPlan = new PlanForm();
        editPlan.setName("Gym VIP Giá Mới");
        editPlan.setKind(PlanKind.GYM);
        editPlan.setDurationDays(60);
        editPlan.setPriceVnd(999_000L);
        planService.update(1L, editPlan);

        var oldReceiptAfterEdit = saleService.getReceipt(results.successes().get(0).paymentId());
        assertEquals("Gym 30 ngày", oldReceiptAfterEdit.planNameSnapshot());
        assertEquals(300_000L, oldReceiptAfterEdit.priceVndSnapshot());
        assertEquals(30, oldReceiptAfterEdit.durationDaysSnapshot());

        // Plan validation: GYM with sessions rejected; PT with 0 sessions rejected
        PlanForm badGym = new PlanForm();
        badGym.setName("Bad GYM");
        badGym.setKind(PlanKind.GYM);
        badGym.setDurationDays(30);
        badGym.setSessionsTotal(5);
        badGym.setPriceVnd(300_000L);
        assertGymError(ErrorCode.INVALID_INPUT, () -> planService.create(badGym));

        // Stale quote submitted after price change -> PRICE_CHANGED
        SaleCommand stalePriceCmd = toCommand(quote, 300_000L, true);
        stalePriceCmd.setRequestKey(UUID.randomUUID().toString());
        stalePriceCmd.setMemberId(3L); // Chi (expired GYM)
        assertGymError(ErrorCode.PRICE_CHANGED, () -> saleService.sell(stalePriceCmd));
        assertNotNull(ptReceipt);
    }

    @Test
    void tc12_tc13_tc14_gymOnlyRenewalChainingConcurrencyAndLeapYearDates() {
        clock.setDateTime(LocalDateTime.of(2024, 2, 28, 10, 0));
        authenticateAs("reception");

        var member = memberService.create(new MemberForm("Test Leap", "0922222222", LocalDate.of(1995, 1, 1)));
        // Purchase PT 90 days (ends 2024-05-28) and GYM 30 days (leap year: 2024-02-28 + 30 = 2024-03-29)
        var ptQ = saleService.prepareQuote(member.id(), 5L, SaleType.PURCHASE, null);
        saleService.sell(toCommand(ptQ, 3_300_000L, true));

        var gymQ = saleService.prepareQuote(member.id(), 1L, SaleType.PURCHASE, null);
        assertEquals(LocalDate.of(2024, 2, 28), gymQ.expectedStart());
        assertEquals(LocalDate.of(2024, 3, 29), gymQ.expectedEnd());
        saleService.sell(toCommand(gymQ, 300_000L, true));

        // Renewal must chain from GYM end (2024-03-29), NOT PT end (2024-05-28)!
        var renewQ1 = saleService.prepareQuote(member.id(), 1L, SaleType.RENEWAL, null);
        assertEquals(LocalDate.of(2024, 3, 29), renewQ1.expectedStart());
        assertEquals(LocalDate.of(2024, 4, 28), renewQ1.expectedEnd());

        // TC-13: Two concurrent renewals with same quote from 2 connections -> 1 commits, 1 gets COVERAGE_CHANGED
        SaleQuote renewQ2 = saleService.prepareQuote(member.id(), 1L, SaleType.RENEWAL, null);
        var conc = runConcurrently(
                () -> {
                    authenticateAs("reception");
                    return saleService.sell(toCommand(renewQ1, 300_000L, true));
                },
                () -> {
                    authenticateAs("reception");
                    return saleService.sell(toCommand(renewQ2, 300_000L, true));
                }
        );
        assertEquals(1, conc.successes().size());
        assertEquals(1, conc.failures().size());
        assertEquals(ErrorCode.COVERAGE_CHANGED, ((GymException) conc.failures().get(0)).getCode());

        // TC-14: Status boundaries SCHEDULED / ACTIVE / EXPIRED
        var details = memberService.getDetail(member.id()).memberships();
        var renewed = details.stream().filter(d -> d.membership().saleType() == SaleType.RENEWAL).findFirst().orElseThrow();
        assertEquals(MembershipStatus.SCHEDULED, renewed.status()); // at 2024-02-28
        clock.setDate(LocalDate.of(2024, 3, 29));
        assertEquals(MembershipStatus.ACTIVE, renewed.membership().statusAt(LocalDate.now(clock)));
        clock.setDate(LocalDate.of(2024, 4, 27));
        assertEquals(MembershipStatus.ACTIVE, renewed.membership().statusAt(LocalDate.now(clock)));
        clock.setDate(LocalDate.of(2024, 4, 28));
        assertEquals(MembershipStatus.EXPIRED, renewed.membership().statusAt(LocalDate.now(clock)));
    }

    @Test
    void tc16_tc17_tc18_checkInBoundariesConcurrencyAndCheckOut() {
        LocalDate today = LocalDate.now(clock);
        authenticateAs("reception");

        // An (id=1) only has PT, no GYM -> NO_ACTIVE_MEMBERSHIP
        clock.setDateTime(today.atTime(10, 0));
        assertGymError(ErrorCode.NO_ACTIVE_MEMBERSHIP, () -> visitService.checkIn(1L));

        // Opening hours boundary on Bình (id=2, active GYM)
        clock.setDateTime(today.atTime(4, 59));
        assertGymError(ErrorCode.OUTSIDE_OPENING_HOURS, () -> visitService.checkIn(2L));
        clock.setDateTime(today.atTime(22, 0));
        assertGymError(ErrorCode.OUTSIDE_OPENING_HOURS, () -> visitService.checkIn(2L));

        // Stale open visit on Hà (id=5) blocks new check-in until closed
        clock.setDateTime(today.atTime(9, 0));
        assertGymError(ErrorCode.ALREADY_INSIDE, () -> visitService.checkIn(5L));

        // TC-17: Concurrent check-in for Bình at 05:00 -> only 1 succeeds, 1 gets ALREADY_INSIDE
        clock.setDateTime(today.atTime(5, 0));
        var res = runConcurrently(
                () -> {
                    authenticateAs("reception");
                    return visitService.checkIn(2L);
                },
                () -> {
                    authenticateAs("reception");
                    return visitService.checkIn(2L);
                }
        );
        assertEquals(1, res.successes().size());
        assertEquals(1, res.failures().size());
        assertEquals(ErrorCode.ALREADY_INSIDE, ((GymException) res.failures().get(0)).getCode());

        // Check-out after 22:00 is allowed, and repeat check-out is idempotent
        long openId = res.successes().get(0).id();
        clock.setDateTime(today.atTime(22, 30));
        var closed1 = visitService.checkOut(openId);
        clock.setDateTime(today.atTime(22, 45));
        var closed2 = visitService.checkOut(openId);
        assertEquals(closed1.checkOutAt(), closed2.checkOutAt());
    }

    @Test
    void tc27_userManagementAndConcurrentLastManagerProtection() {
        authenticateAs("manager");

        // Password < 8 bytes and > 72 UTF-8 bytes rejected
        UserCreateInput shortPwd = new UserCreateInput();
        shortPwd.setUsername("staff01");
        shortPwd.setFullName("Staff 01");
        shortPwd.setRole(Role.RECEPTIONIST);
        shortPwd.setPassword("1234567");
        assertGymError(ErrorCode.INVALID_INPUT, () -> userService.create(shortPwd));

        UserCreateInput longUtf8Pwd = new UserCreateInput();
        longUtf8Pwd.setUsername("staff01");
        longUtf8Pwd.setFullName("Staff 01");
        longUtf8Pwd.setRole(Role.RECEPTIONIST);
        longUtf8Pwd.setPassword("á".repeat(37)); // 74 UTF-8 bytes
        assertGymError(ErrorCode.INVALID_INPUT, () -> userService.create(longUtf8Pwd));

        // Create second manager & self-lock rejected
        UserCreateInput mgr2Input = new UserCreateInput();
        mgr2Input.setUsername("manager2");
        mgr2Input.setFullName("Quản lý 2");
        mgr2Input.setRole(Role.MANAGER);
        mgr2Input.setPassword("Manager@123");
        var mgr2 = userService.create(mgr2Input);

        assertGymError(ErrorCode.LAST_MANAGER_OR_SELF, () -> userService.setActive(1L, false));

        // Concurrent mutual deactivation: mgr1 locks mgr2 while mgr2 locks mgr1 -> only 1 succeeds!
        var conc = runConcurrently(
                () -> {
                    authenticateAs("manager");
                    userService.setActive(mgr2.id(), false);
                    return "mgr1_locked_mgr2";
                },
                () -> {
                    authenticateAs("manager2");
                    userService.setActive(1L, false);
                    return "mgr2_locked_mgr1";
                }
        );
        assertEquals(1, conc.successes().size(), "Exactly one manager lock may succeed");
        assertEquals(1, conc.failures().size(), "Second manager lock must fail to protect last active manager");
        assertEquals(ErrorCode.LAST_MANAGER_OR_SELF, ((GymException) conc.failures().get(0)).getCode());

        // Reset password bumps credential_version
        int beforeVer = userRepository.findById(2L).orElseThrow().credentialVersion();
        authenticateAs("manager");
        // Ensure manager1 is active if manager2 won the race above
        if (!userRepository.findById(1L).orElseThrow().active()) {
            authenticateAs("manager2");
        }
        userService.resetPassword(2L, "NewPass@2026");
        assertEquals(beforeVer + 1, userRepository.findById(2L).orElseThrow().credentialVersion());
    }

    @Test
    void tc28_to_tc38_tc40_tc41_ptAvailabilityBookingConcurrencyAndResolution() {
        clock.setDateTime(LocalDateTime.of(2026, 11, 17, 10, 0));
        authenticateAs("manager");

        // Create new member with GYM + 2 separate PT contracts + new trainer
        var member = memberService.create(new MemberForm("Khách PT Test", "0933333333", LocalDate.of(1997, 5, 5)));
        var gq = saleService.prepareQuote(member.id(), 1L, SaleType.PURCHASE, null);
        saleService.sell(toCommand(gq, 300_000L, true));

        var pt1Q = saleService.prepareQuote(member.id(), 4L, SaleType.PURCHASE, null);
        long ptContract1 = saleService.sell(toCommand(pt1Q, 1_200_000L, true)).membershipId();

        // Create 1-session PT plan to test last credit race (TC-35)
        PlanForm oneSessionPlan = new PlanForm();
        oneSessionPlan.setName("PT 1 buổi thử");
        oneSessionPlan.setKind(PlanKind.PT);
        oneSessionPlan.setDurationDays(30);
        oneSessionPlan.setSessionsTotal(1);
        oneSessionPlan.setPriceVnd(400_000L);
        var p1 = planService.create(oneSessionPlan);
        var ptLastQ = saleService.prepareQuote(member.id(), p1.id(), SaleType.PURCHASE, null);
        long lastCreditContract = saleService.sell(toCommand(ptLastQ, 400_000L, true)).membershipId();

        // Declare slots 08:00-12:00 on 2026-11-19 (@d + 2, free of seed slots) for trainer 1 & trainer 2
        LocalDate bookDay = LocalDate.of(2026, 11, 19);
        AvailabilityForm av1 = new AvailabilityForm();
        av1.setDate(bookDay);
        av1.setStartTime(LocalTime.of(8, 0));
        av1.setEndTime(LocalTime.of(12, 0));
        long slotT1 = availabilityService.create(1L, av1).id();
        long slotT2 = availabilityService.create(2L, av1).id();

        // Overlapping availability rejected
        AvailabilityForm overlapAv = new AvailabilityForm();
        overlapAv.setDate(bookDay);
        overlapAv.setStartTime(LocalTime.of(11, 0));
        overlapAv.setEndTime(LocalTime.of(13, 0));
        assertGymError(ErrorCode.AVAILABILITY_CONFLICT, () -> availabilityService.create(1L, overlapAv));

        // TC-35a: Two connections race to book the same trainer 1 in 08:00-09:30 window
        BookingInput b1 = bookingInput(member.id(), ptContract1, 1L, bookDay, LocalTime.of(8, 0));
        BookingInput bOverlap = bookingInput(member.id(), ptContract1, 1L, bookDay, LocalTime.of(8, 30));
        var raceSlot = runConcurrently(
                () -> {
                    authenticateAs("reception");
                    return bookingService.book(b1);
                },
                () -> {
                    authenticateAs("reception");
                    return bookingService.book(bOverlap);
                }
        );
        assertEquals(1, raceSlot.successes().size());
        assertEquals(1, raceSlot.failures().size());
        ErrorCode slotErr = ((GymException) raceSlot.failures().get(0)).getCode();
        assertTrue(slotErr == ErrorCode.TRAINER_CONFLICT || slotErr == ErrorCode.MEMBER_CONFLICT);

        long bookedId = raceSlot.successes().get(0).id();

        // Cannot delete availability slot or deactivate trainer while BOOKED exists
        authenticateAs("manager");
        assertGymError(ErrorCode.PENDING_BOOKINGS, () -> availabilityService.delete(slotT1));
        assertGymError(ErrorCode.PENDING_BOOKINGS, () -> trainerService.setActive(1L, false));

        // TC-35b: Two connections race for the LAST credit (available=1) of lastCreditContract across PT1 and PT2 at different hours!
        BookingInput c1 = bookingInput(member.id(), lastCreditContract, 1L, bookDay, LocalTime.of(10, 0));
        BookingInput c2 = bookingInput(member.id(), lastCreditContract, 2L, bookDay, LocalTime.of(11, 0));
        var raceQuota = runConcurrently(
                () -> {
                    authenticateAs("reception");
                    return bookingService.book(c1);
                },
                () -> {
                    authenticateAs("reception");
                    return bookingService.book(c2);
                }
        );
        assertEquals(1, raceQuota.successes().size());
        assertEquals(1, raceQuota.failures().size());
        assertEquals(ErrorCode.NO_PT_CREDIT, ((GymException) raceQuota.failures().get(0)).getCode());

        // Verify exact duplicate BOOKED is idempotent (does not consume another credit)
        long winningBookingId = raceQuota.successes().get(0).id();
        long winningTrainerId = raceQuota.successes().get(0).trainerId();
        var winningStart = raceQuota.successes().get(0).startAt();
        authenticateAs("reception");
        var dupResult = bookingService.book(bookingInput(
                member.id(), lastCreditContract, winningTrainerId, winningStart.toLocalDate(), winningStart.toLocalTime()
        ));
        assertEquals(winningBookingId, dupResult.id());

        // TC-37: Complete requires now >= endAt and overlapping GYM visit
        authenticateAs(winningTrainerId == 1L ? "ptminh" : "ptnam");
        assertGymError(ErrorCode.SESSION_NOT_ENDED, () -> bookingService.complete(winningBookingId));

        // Advance to after session end WITHOUT a visit -> NO_ATTENDANCE
        clock.setDateTime(winningStart.plusMinutes(65));
        assertGymError(ErrorCode.NO_ATTENDANCE, () -> bookingService.complete(winningBookingId));

        // Other trainer cannot complete -> FORBIDDEN (TC-40)
        authenticateAs(winningTrainerId == 1L ? "ptnam" : "ptminh");
        assertGymError(ErrorCode.FORBIDDEN, () -> bookingService.complete(winningBookingId));

        // Create check-in overlapping [winningStart, winningStart+60m) -> complete succeeds!
        authenticateAs("reception");
        clock.setDateTime(winningStart.plusMinutes(10));
        var v = visitService.checkIn(member.id());
        clock.setDateTime(winningStart.plusMinutes(70));
        visitService.checkOut(v.id());

        authenticateAs(winningTrainerId == 1L ? "ptminh" : "ptnam");
        var completed = bookingService.complete(winningBookingId);
        assertEquals(BookingStatus.COMPLETED, completed.status());

        // Repeat complete is idempotent; flipping to NO_SHOW fails with INVALID_STATE
        assertEquals(BookingStatus.COMPLETED, bookingService.complete(winningBookingId).status());
        assertGymError(ErrorCode.INVALID_STATE, () -> bookingService.markNoShow(winningBookingId));

        // Cancel bookedId after start fails with CANCELLATION_TOO_LATE (since clock is on 2026-11-18 >= 09:30)
        // Reset clock before bookedId.startAt() to cancel cleanly, then verify slotT1 and trainer 1 can proceed
        authenticateAs("reception");
        var bookedRow = bookingService.search(bookDay, null, member.id(), "BOOKED", 0).items().get(0);
        assertGymError(ErrorCode.CANCELLATION_TOO_LATE, () -> bookingService.cancel(bookedId));
        clock.setDateTime(bookedRow.startAt().minusMinutes(5));
        var cancelled = bookingService.cancel(bookedId);
        assertEquals(BookingStatus.CANCELLED, cancelled.status());

        // Now availability slotT1 has 0 BOOKED -> delete allowed; slotT2 untouched
        authenticateAs("manager");
        availabilityService.delete(slotT1);
        assertTrue(slotT2 > 0);
    }

    @Test
    void tc39_tc42_expiringReportAndEquipmentWorkflow() {
        LocalDate today = LocalDate.of(2026, 11, 17);
        clock.setDateTime(today.atTime(10, 0));
        authenticateAs("manager");

        var expiringMember = memberService.create(new MemberForm("Sắp Hết Hạn", "0944444444", LocalDate.of(1998, 1, 1)));
        var renewedMember = memberService.create(new MemberForm("Đã Gia Hạn Xa", "0955555555", LocalDate.of(1998, 1, 1)));

        PlanForm p5 = new PlanForm();
        p5.setName("Gym 5 ngày");
        p5.setKind(PlanKind.GYM);
        p5.setDurationDays(5);
        p5.setPriceVnd(100_000L);
        var plan5 = planService.create(p5);

        // expiringMember: [17/11, 22/11) -> lastUsable = 21/11 (4 days remaining, inside 7-day window!)
        saleService.sell(toCommand(saleService.prepareQuote(expiringMember.id(), plan5.id(), SaleType.PURCHASE, null), 100_000L, true));

        // renewedMember: buys 5d then renews 30d -> MAX(end_date) = 22/12 -> excluded from 7-day list!
        saleService.sell(toCommand(saleService.prepareQuote(renewedMember.id(), plan5.id(), SaleType.PURCHASE, null), 100_000L, true));
        saleService.sell(toCommand(saleService.prepareQuote(renewedMember.id(), 1L, SaleType.RENEWAL, null), 300_000L, true));

        var expiringList = reportService.expiringGym();
        assertTrue(expiringList.stream().anyMatch(e -> e.memberId() == expiringMember.id()));
        assertFalse(expiringList.stream().anyMatch(e -> e.memberId() == renewedMember.id()));

        // Report max range 366 days enforced
        assertGymError(ErrorCode.INVALID_INPUT, () -> reportService.summarize(today, today.plusDays(366)));

        // TC-42 Equipment Should
        EquipmentForm dupCode = new EquipmentForm();
        dupCode.setCode("TM01");
        dupCode.setName("Duplicate");
        dupCode.setLocation("Cardio");
        assertGymError(ErrorCode.INVALID_INPUT, () -> equipmentService.create(dupCode));

        authenticateAs("ptminh");
        var broken = equipmentService.reportBroken(1L, "Mất nguồn đột ngột");
        assertEquals(EquipmentStatus.BROKEN, broken.status());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> equipmentService.changeStatus(1L, EquipmentStatus.AVAILABLE, "Tự sửa"));

        authenticateAs("manager");
        var maint = equipmentService.changeStatus(1L, EquipmentStatus.MAINTENANCE, "Đã gọi kỹ thuật");
        assertEquals(EquipmentStatus.MAINTENANCE, maint.status());
        var fixed = equipmentService.changeStatus(1L, EquipmentStatus.AVAILABLE, "Thay cầu chì xong");
        assertEquals(EquipmentStatus.AVAILABLE, fixed.status());
    }

    @Test
    void tc19_tc22_tc23_constraintsNonDestructiveBootstrapAndVisitPagination() {
        // TC-22: DB-level CHECK, FK, and UNIQUE invariants reject invalid rows
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO payments (membership_id, amount_vnd, method, paid_at, collected_by) VALUES (1, 0, 'CASH', NOW(), 2)"));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO payments (membership_id, amount_vnd, method, paid_at, collected_by) VALUES (1, 300000, 'CASH', NOW(), 2)"));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO visits (member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by) "
                        + "VALUES (5, 4, NOW(), NULL, 2, NULL)"));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO visits (member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by) "
                        + "VALUES (1, 1, NOW(), NULL, 2, NULL)"));

        // TC-23: Running DatabaseBootstrap again on populated DB must preserve all data untouched
        long countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Long.class);
        long paidBefore = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount_vnd), 0) FROM payments", Long.class);
        var bootstrap = new vn.edu.gym.config.DatabaseBootstrap(
                dataSource, jdbcTemplate, passwordEncoder, true, "Gym@12345"
        );
        bootstrap.run(null);
        assertEquals(countBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memberships", Long.class));
        assertEquals(paidBefore, jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount_vnd), 0) FROM payments", Long.class));

        // TC-19: Visit search pagination & date boundary [from 00:00, to+1 00:00)
        authenticateAs("reception");
        LocalDate today = LocalDate.now(clock);
        var visitsAll = visitService.search(null, null, today.minusDays(5), today, 0);
        assertEquals(3, visitsAll.totalItems());
        var visitsOnlyTwoDaysAgo = visitService.search(2L, null, today.minusDays(2), today.minusDays(2), 0);
        assertEquals(1, visitsOnlyTwoDaysAgo.totalItems());
    }

    @Test
    void tc20_performanceBenchmark1000Members10000Visits() {
        // Seed 1,000 members + 1,000 GYM memberships + 10,000 visits in batches
        StringBuilder memberSql = new StringBuilder(
                "INSERT INTO members (full_name, phone, birth_date, active, created_at, created_by) VALUES ");
        for (int i = 1; i <= 1000; i++) {
            if (i > 1) memberSql.append(',');
            memberSql.append("('Perf Member ").append(i).append("','")
                    .append(String.format("097%07d", i))
                    .append("','1995-01-01',TRUE,'2026-11-01 08:00:00',1)");
        }
        jdbcTemplate.execute(memberSql.toString());

        List<Long> perfIds = jdbcTemplate.queryForList(
                "SELECT id FROM members WHERE phone LIKE '097%' ORDER BY id ASC", Long.class);
        assertEquals(1000, perfIds.size());

        StringBuilder msSql = new StringBuilder(
                "INSERT INTO memberships (member_id, plan_id, sale_type, request_key, plan_name_snapshot, "
                        + "kind_snapshot, duration_days_snapshot, sessions_total_snapshot, price_vnd_snapshot, "
                        + "start_date, end_date, sold_at, sold_by) VALUES ");
        for (int i = 0; i < perfIds.size(); i++) {
            if (i > 0) msSql.append(',');
            msSql.append('(').append(perfIds.get(i)).append(",1,'PURCHASE','")
                    .append(UUID.randomUUID())
                    .append("','Gym 30 ngày','GYM',30,NULL,300000,'2026-11-01','2026-12-01','2026-11-01 08:00:00',2)");
        }
        jdbcTemplate.execute(msSql.toString());

        List<Long> msIds = jdbcTemplate.queryForList(
                "SELECT ms.id FROM memberships ms JOIN members m ON m.id=ms.member_id "
                        + "WHERE m.phone LIKE '097%' ORDER BY m.id ASC", Long.class);

        for (int batch = 0; batch < 10; batch++) {
            StringBuilder vSql = new StringBuilder(
                    "INSERT INTO visits (member_id, membership_id, check_in_at, check_out_at, check_in_by, check_out_by) VALUES ");
            for (int j = 0; j < 1000; j++) {
                int idx = j;
                if (j > 0) vSql.append(',');
                vSql.append('(').append(perfIds.get(idx)).append(',').append(msIds.get(idx))
                        .append(",'2026-11-").append(String.format("%02d", (batch % 15) + 1))
                        .append(" 09:00:00','2026-11-").append(String.format("%02d", (batch % 15) + 1))
                        .append(" 10:00:00',2,2)");
            }
            jdbcTemplate.execute(vSql.toString());
        }

        authenticateAs("reception");
        for (int w = 0; w < 5; w++) {
            memberService.search("Perf Member 500", 0);
            visitService.search(null, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), 0);
        }

        // TC-20 Raw 20 latencies output
        List<Long> memberMsList = new java.util.ArrayList<>();
        List<Long> visitMsList = new java.util.ArrayList<>();
        int memberWithin2s = 0;
        int visitWithin2s = 0;
        long maxMemberMs = 0;
        long maxVisitMs = 0;
        for (int r = 0; r < 20; r++) {
            long t1 = System.nanoTime();
            var mp = memberService.search("Perf Member " + (r + 100), 0);
            long d1 = (System.nanoTime() - t1) / 1_000_000;
            memberMsList.add(d1);
            maxMemberMs = Math.max(maxMemberMs, d1);
            assertFalse(mp.items().isEmpty());
            if (d1 <= 2000) memberWithin2s++;

            long t2 = System.nanoTime();
            var vp = visitService.search(null, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), r % 5);
            long d2 = (System.nanoTime() - t2) / 1_000_000;
            visitMsList.add(d2);
            maxVisitMs = Math.max(maxVisitMs, d2);
            assertEquals(20, vp.items().size());
            if (d2 <= 2000) visitWithin2s++;
        }
        System.out.println("NFR-01_BENCHMARK member_max_ms=" + maxMemberMs + " visit_max_ms=" + maxVisitMs
                + " member_raw_ms=" + memberMsList + " visit_raw_ms=" + visitMsList);
        assertTrue(memberWithin2s >= 19, "Expected >=19/20 member searches <=2000ms, max=" + maxMemberMs);
        assertTrue(visitWithin2s >= 19, "Expected >=19/20 visit searches <=2000ms, max=" + maxVisitMs);
    }

    @Test
    void qaR03_R04_R05_R06_paginationClampVisit366DaysNoShowAndClockError() {
        LocalDate today = LocalDate.of(2026, 11, 17);
        clock.setDateTime(today.atTime(10, 0));
        authenticateAs("manager");

        // QA-R03: Negative and INT_MAX pagination clamp across all 3 repositories without overflow or 500
        var mNeg = memberRepository.search("", -5, 20);
        var mMax = memberRepository.search("", Integer.MAX_VALUE, 20);
        assertEquals(0, mNeg.page());
        assertEquals(0, mMax.page());
        assertEquals(6, mMax.items().size());

        var vNeg = visitRepository.search(null, "", null, null, today, -1, 20);
        var vMax = visitRepository.search(null, "", null, null, today, Integer.MAX_VALUE, 20);
        assertEquals(0, vNeg.page());
        assertEquals(0, vMax.page());
        assertEquals(3, vMax.items().size());

        var bNeg = bookingRepository.search(null, null, null, "ALL", today.atTime(10, 0), -1, 20);
        var bMax = bookingRepository.search(null, null, null, "ALL", today.atTime(10, 0), Integer.MAX_VALUE, 20);
        assertEquals(0, bNeg.page());
        assertEquals(0, bMax.page());
        assertEquals(4, bMax.items().size());

        // QA-R04: VisitService & ReportService 366-day max and missing-one-date enforcement
        assertDoesNotThrow(() -> visitService.search(null, null, today.minusDays(365), today, 0));
        assertGymError(ErrorCode.INVALID_INPUT, () -> visitService.search(null, null, today.minusDays(366), today, 0));
        assertGymError(ErrorCode.INVALID_INPUT, () -> visitService.search(null, null, today, today.minusDays(1), 0));
        assertGymError(ErrorCode.INVALID_INPUT, () -> visitService.search(null, null, today, null, 0));
        assertGymError(ErrorCode.INVALID_INPUT, () -> visitService.search(null, null, null, today, 0));
        assertDoesNotThrow(() -> visitService.search(null, null, null, null, 0));

        assertDoesNotThrow(() -> reportService.summarize(today, today.plusDays(365)));
        assertGymError(ErrorCode.INVALID_INPUT, () -> reportService.summarize(today, null));
        assertGymError(ErrorCode.INVALID_INPUT, () -> reportService.summarize(null, today));

        // QA-R05: Full BOOKED -> NO_SHOW lifecycle on booking #2 (PT #7 initially: total=4, used=1, reserved=2, available=1)
        LocalDateTime b2Start = today.plusDays(1).atTime(17, 0);
        LocalDateTime b2End = b2Start.plusHours(1);
        authenticateAs("ptminh");
        assertGymError(ErrorCode.SESSION_NOT_ENDED, () -> bookingService.markNoShow(2L));

        // Wrong trainer cannot mark NO_SHOW
        clock.setDateTime(b2End);
        authenticateAs("ptnam");
        assertGymError(ErrorCode.FORBIDDEN, () -> bookingService.markNoShow(2L));

        // Assigned trainer marks NO_SHOW right at endAt -> succeeds, reserved 2 -> 1, used stays 1, available 1 -> 2
        authenticateAs("ptminh");
        var noShow = bookingService.markNoShow(2L);
        assertEquals(BookingStatus.NO_SHOW, noShow.status());
        // Repeat markNoShow is idempotent
        assertEquals(BookingStatus.NO_SHOW, bookingService.markNoShow(2L).status());
        // Cannot flip NO_SHOW -> COMPLETED
        assertGymError(ErrorCode.INVALID_STATE, () -> bookingService.complete(2L));

        authenticateAs("manager");
        var binContract7 = memberService.getDetail(2L).memberships().stream()
                .filter(c -> c.membership().id() == 7L)
                .findFirst().orElseThrow();
        assertEquals(1, binContract7.quota().used());
        assertEquals(1, binContract7.quota().reserved());
        assertEquals(2, binContract7.quota().available());

        // QA-R06 (TC-18): CLOCK_ERROR when clock < checkInAt during checkOut (seed visit #1 check-in is yesterday 18:00)
        authenticateAs("reception");
        clock.setDateTime(today.minusDays(1).atTime(17, 59));
        assertGymError(ErrorCode.CLOCK_ERROR, () -> visitService.checkOut(1L));
    }

    private SaleCommand toCommand(SaleQuote q, long amountVnd, boolean cashConfirmed) {
        SaleCommand cmd = new SaleCommand();
        cmd.setMemberId(q.memberId());
        cmd.setPlanId(q.planId());
        cmd.setSaleType(q.saleType());
        cmd.setRequestKey(q.requestKey());
        cmd.setQuotedKind(q.quotedKind());
        cmd.setQuotedDurationDays(q.quotedDurationDays());
        cmd.setQuotedSessionsTotal(q.quotedSessionsTotal());
        cmd.setQuotedPriceVnd(q.quotedPriceVnd());
        cmd.setExpectedStart(q.expectedStart());
        cmd.setExpectedEnd(q.expectedEnd());
        cmd.setAmountReceivedVnd(amountVnd);
        cmd.setCashConfirmed(cashConfirmed);
        return cmd;
    }

    private BookingInput bookingInput(long memberId, long membershipId, long trainerId, LocalDate date, LocalTime time) {
        BookingInput bi = new BookingInput();
        bi.setMemberId(memberId);
        bi.setMembershipId(membershipId);
        bi.setTrainerId(trainerId);
        bi.setBookingDate(date);
        bi.setStartTime(time);
        return bi;
    }

    private <T> ConcurrentOutcome<T> runConcurrently(Callable<T> task1, Callable<T> task2) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<AttemptResult<T>> w1 = wrap(task1, ready, start);
            Callable<AttemptResult<T>> w2 = wrap(task2, ready, start);
            Future<AttemptResult<T>> f1 = pool.submit(w1);
            Future<AttemptResult<T>> f2 = pool.submit(w2);
            ready.await();
            start.countDown();
            AttemptResult<T> r1 = f1.get();
            AttemptResult<T> r2 = f2.get();
            List<T> successes = new java.util.ArrayList<>();
            List<Throwable> failures = new java.util.ArrayList<>();
            for (AttemptResult<T> r : List.of(r1, r2)) {
                if (r.error() == null) {
                    successes.add(r.value());
                } else {
                    failures.add(r.error());
                }
            }
            return new ConcurrentOutcome<>(successes, failures);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    private <T> Callable<AttemptResult<T>> wrap(Callable<T> task, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            try {
                ready.countDown();
                start.await();
                return new AttemptResult<>(task.call(), null);
            } catch (Throwable t) {
                return new AttemptResult<>(null, t);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private record AttemptResult<T>(T value, Throwable error) {
    }

    private record ConcurrentOutcome<T>(List<T> successes, List<Throwable> failures) {
    }
}
