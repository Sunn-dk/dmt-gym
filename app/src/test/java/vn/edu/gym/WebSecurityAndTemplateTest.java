package vn.edu.gym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;
import vn.edu.gym.config.ClockConfig;
import vn.edu.gym.config.PasswordEncoderConfig;
import vn.edu.gym.controller.AppErrorController;
import vn.edu.gym.controller.AuthController;
import vn.edu.gym.controller.BookingController;
import vn.edu.gym.controller.EquipmentController;
import vn.edu.gym.controller.GlobalExceptionHandler;
import vn.edu.gym.controller.GlobalModelAdvice;
import vn.edu.gym.controller.MemberController;
import vn.edu.gym.controller.PlanController;
import vn.edu.gym.controller.ReportController;
import vn.edu.gym.controller.SaleController;
import vn.edu.gym.controller.TrainerController;
import vn.edu.gym.controller.UserController;
import vn.edu.gym.controller.VisitController;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.AvailabilityForm;
import vn.edu.gym.model.AvailabilitySlot;
import vn.edu.gym.model.BookingStatus;
import vn.edu.gym.model.Equipment;
import vn.edu.gym.model.EquipmentStatus;
import vn.edu.gym.model.ExpiringMemberView;
import vn.edu.gym.model.Member;
import vn.edu.gym.model.MemberListItem;
import vn.edu.gym.model.Membership;
import vn.edu.gym.model.MembershipDetailView;
import vn.edu.gym.model.MembershipStatus;
import vn.edu.gym.model.PageResult;
import vn.edu.gym.model.Plan;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.PtBookingView;
import vn.edu.gym.model.QuotaView;
import vn.edu.gym.model.ReceiptView;
import vn.edu.gym.model.ReportSummary;
import vn.edu.gym.model.Role;
import vn.edu.gym.model.SaleQuote;
import vn.edu.gym.model.SaleType;
import vn.edu.gym.model.Trainer;
import vn.edu.gym.model.UserAccount;
import vn.edu.gym.model.VisitView;
import vn.edu.gym.repository.MemberRepository;
import vn.edu.gym.repository.UserRepository;
import vn.edu.gym.security.CredentialVersionFilter;
import vn.edu.gym.security.GymUserDetails;
import vn.edu.gym.security.GymUserDetailsService;
import vn.edu.gym.security.SecurityConfig;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = {
        AppErrorController.class,
        AuthController.class,
        UserController.class,
        MemberController.class,
        PlanController.class,
        SaleController.class,
        VisitController.class,
        TrainerController.class,
        BookingController.class,
        ReportController.class,
        EquipmentController.class
})
@Import({
        ClockConfig.class,
        PasswordEncoderConfig.class,
        SecurityConfig.class,
        CredentialVersionFilter.class,
        GymUserDetailsService.class,
        GlobalModelAdvice.class,
        GlobalExceptionHandler.class
})
class WebSecurityAndTemplateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private MemberRepository memberRepository;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private PlanService planService;
    @MockitoBean
    private SaleService saleService;
    @MockitoBean
    private VisitService visitService;
    @MockitoBean
    private TrainerService trainerService;
    @MockitoBean
    private AvailabilityService availabilityService;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private EquipmentService equipmentService;

    private GymUserDetails principal(long id, String username, Role role, Long trainerId, int version) {
        UserAccount ua = new UserAccount(id, username, "$2a$12$dummy", "Test " + username, role, trainerId, version, true, null);
        when(userRepository.findById(id)).thenReturn(Optional.of(ua));
        return new GymUserDetails(ua);
    }

    @Test
    void unauthenticatedRedirectsToLogin_andMissingCsrfIsRejected() throws Exception {
        mockMvc.perform(get("/members"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng nhập hệ thống")))
                .andExpect(content().string(containsString("btnThemeDark")))
                .andExpect(content().string(not(containsString("UI-01"))))
                .andExpect(content().string(not(containsString("Spring Boot"))))
                .andExpect(content().string(not(containsString("Hội viên không đăng nhập"))));

        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);
        mockMvc.perform(post("/plans").with(user(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginRedirectsByRole_andCredentialVersionMismatchInvalidatesSession() throws Exception {
        String hash = passwordEncoder.encode("Gym@12345");
        UserAccount trainerUser = new UserAccount(3L, "ptminh", hash, "PT mẫu Minh", Role.TRAINER, 1L, 0, true, "PT mẫu Minh");
        when(userRepository.findByUsername("ptminh")).thenReturn(Optional.of(trainerUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(trainerUser));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "ptminh")
                        .param("password", "Gym@12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pt-bookings"));

        // Credential version bumped in DB while session still holds version 0 -> invalidated!
        GymUserDetails staleSession = new GymUserDetails(trainerUser);
        UserAccount bumpedUser = new UserAccount(3L, "ptminh", hash, "PT mẫu Minh", Role.TRAINER, 1L, 1, true, "PT mẫu Minh");
        when(userRepository.findById(3L)).thenReturn(Optional.of(bumpedUser));

        mockMvc.perform(get("/pt-bookings").with(user(staleSession)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?invalidated"));
    }

    @Test
    void roleAccessControlBlocksUnauthorizedUrlsAndPosts() throws Exception {
        GymUserDetails reception = principal(2L, "reception", Role.RECEPTIONIST, null, 0);
        GymUserDetails trainer = principal(3L, "ptminh", Role.TRAINER, 1L, 0);

        // Receptionist cannot access /users or /reports or POST /plans or /members/*/active or complete PT
        mockMvc.perform(get("/users").with(user(reception))).andExpect(status().isForbidden());
        mockMvc.perform(get("/reports").with(user(reception))).andExpect(status().isForbidden());
        mockMvc.perform(post("/plans").with(user(reception)).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/members/2/active").param("active", "false").with(user(reception)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/pt-bookings/1/complete").with(user(reception)).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/equipment/1/status").param("targetStatus", "AVAILABLE").param("note", "ok")
                .with(user(reception)).with(csrf())).andExpect(status().isForbidden());

        // Trainer cannot access /members, /plans, /receipts, /visits, /reports, /users, or book/cancel PT
        mockMvc.perform(get("/members").with(user(trainer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/plans").with(user(trainer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/receipts/1").with(user(trainer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/visits").with(user(trainer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/reports").with(user(trainer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/users").with(user(trainer))).andExpect(status().isForbidden());
        mockMvc.perform(post("/pt-bookings").with(user(trainer)).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/pt-bookings/1/cancel").with(user(trainer)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void allElevenUiTemplatesRenderCleanlyForManager() throws Exception {
        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);

        Member sampleMember = new Member(2L, "Hội viên mẫu Bình", "0900000002", LocalDate.of(1998, 6, 20), true,
                LocalDateTime.of(2026, 11, 17, 10, 0), 1L, "Quản lý mẫu");
        Membership samplePtMs = new Membership(7L, 2L, 4L, SaleType.PURCHASE, "00000000-0000-4000-8000-000000000007",
                "PT 4 buổi", PlanKind.PT, 30, 4, 1_200_000L,
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 7),
                LocalDateTime.of(2026, 11, 7, 9, 0), 2L);
        MembershipDetailView detailView = new MembershipDetailView(
                samplePtMs, MembershipStatus.ACTIVE, LocalDate.of(2026, 12, 6),
                QuotaView.compute(4, 1, 2), 7L, "Lễ tân mẫu"
        );

        when(memberService.search(any(), anyInt())).thenReturn(
                new PageResult<>(List.of(new MemberListItem(sampleMember, "Đang hiệu lực", "1 HĐ", false)), 0, 20, 1));
        when(memberService.getDetail(2L)).thenReturn(
                new MemberService.MemberDetailResult(sampleMember, List.of(detailView), true, true, false, List.of()));
        when(planService.listAll()).thenReturn(List.of(
                new Plan(1L, "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L, true)));
        when(planService.listActive()).thenReturn(List.of(
                new Plan(1L, "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L, true)));
        when(saleService.prepareQuote(anyLong(), anyLong(), any(), any())).thenReturn(
                new SaleQuote(2L, "Bình", 1L, "Gym 30 ngày", SaleType.RENEWAL, PlanKind.GYM, 30, null, 300_000L,
                        LocalDate.of(2026, 11, 27), LocalDate.of(2026, 12, 27), LocalDate.of(2026, 12, 26),
                        "00000000-0000-4000-8000-000000000099"));
        when(saleService.getReceipt(1L)).thenReturn(
                new ReceiptView(1L, 1L, 2L, "Bình", "0900000002", "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L,
                        SaleType.PURCHASE, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 26),
                        300_000L, "CASH", LocalDateTime.of(2026, 10, 28, 9, 0), 2L, "Lễ tân mẫu"));
        when(visitService.listOpenVisits()).thenReturn(List.of());
        when(visitService.search(any(), any(), any(), any(), anyInt())).thenReturn(
                new PageResult<>(List.of(new VisitView(1L, 5L, "Hà", "0900000005", 4L, "Gym 30 ngày",
                        LocalDateTime.of(2026, 11, 16, 18, 0), null, 2L, "Lễ tân mẫu", null, null, true, true)), 0, 20, 1));
        when(memberRepository.findAllBasicForSelect()).thenReturn(List.of(sampleMember));
        when(memberRepository.findAllActiveForSelect()).thenReturn(List.of(sampleMember));
        when(trainerService.listForCurrentActor()).thenReturn(List.of(new Trainer(1L, "PT mẫu Minh", "Sức mạnh", true)));
        when(trainerService.listAllActive()).thenReturn(List.of(new Trainer(1L, "PT mẫu Minh", "Sức mạnh", true)));
        when(availabilityService.listForCurrentActor(any(), any())).thenReturn(List.of());
        when(bookingService.listOverduePending()).thenReturn(List.of());
        when(bookingService.listPtContractsForMember(anyLong())).thenReturn(List.of(detailView));
        when(bookingService.search(any(), any(), any(), any(), anyInt())).thenReturn(
                new PageResult<>(List.of(new PtBookingView(1L, 2L, "Bình", 7L, "PT 4 buổi", 1L, "PT Minh",
                        LocalDateTime.of(2026, 11, 15, 17, 0), LocalDateTime.of(2026, 11, 15, 18, 0),
                        BookingStatus.COMPLETED, false, false, false, "",
                        LocalDateTime.of(2026, 11, 14, 10, 0), 2L, "Lễ tân",
                        LocalDateTime.of(2026, 11, 15, 18, 5), 3L, "PT Minh")), 0, 20, 1));
        when(reportService.summarize(any(), any())).thenReturn(
                new ReportSummary(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17), 4_700_000L, 3L, 1L));
        when(reportService.expiringGym()).thenReturn(List.of(
                new ExpiringMemberView(2L, "Bình", "0900000002", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 19), 2L)));
        when(userService.listAll()).thenReturn(List.of(
                new UserAccount(1L, "manager", "$2a$12$x", "Quản lý mẫu", Role.MANAGER, null, 0, true, null)));
        when(userService.listAvailableTrainersForUserCreation()).thenReturn(List.of());
        when(equipmentService.listAll(any())).thenReturn(List.of(
                new Equipment(1L, "TM01", "Máy chạy bộ 01", "Khu cardio", EquipmentStatus.AVAILABLE, "Kiểm tra mẫu",
                        LocalDateTime.of(2026, 11, 17, 8, 0), 1L, "Quản lý mẫu")));

        // Verify UI-02 through UI-11 render HTTP 200 without Thymeleaf errors
        mockMvc.perform(get("/members").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hồ sơ hội viên")));
        mockMvc.perform(get("/members/2").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("HV-2")));
        mockMvc.perform(get("/plans").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Gym 30 ngày")));
        mockMvc.perform(get("/members/2/sales/new").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Xác nhận nhận đủ tiền mặt")));
        mockMvc.perform(get("/receipts/1").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("PT-1")));
        mockMvc.perform(get("/visits").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý ra vào phòng gym")));
        mockMvc.perform(get("/trainers").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("PT mẫu Minh")));
        mockMvc.perform(get("/pt-bookings").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("BK-1")));
        mockMvc.perform(get("/reports").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("4.700.000 đ")));
        mockMvc.perform(get("/users").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tài khoản nhân viên")));
        mockMvc.perform(get("/equipment").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("TM01")));

        // Verify role-scoped rendering for TRAINER and RECEPTIONIST + 403 view
        GymUserDetails trainer = principal(3L, "ptminh", Role.TRAINER, 1L, 0);
        GymUserDetails reception = principal(2L, "reception", Role.RECEPTIONIST, null, 0);
        mockMvc.perform(get("/pt-bookings").with(user(trainer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("BK-1")));
        mockMvc.perform(get("/trainers").with(user(trainer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("PT mẫu Minh")));
        mockMvc.perform(get("/plans").with(user(reception))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Gym 30 ngày")));
        mockMvc.perform(get("/users").with(user(reception))).andExpect(status().isForbidden());
    }

    @Test
    void dmtGymBrandingAndFaviconAndSkipLinkPresent() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(containsString("DMT GYM")))
                .andExpect(content().string(containsString("login-main")))
                .andExpect(content().string(containsString("skip-link")))
                .andExpect(content().string(containsString("tabindex=\"-1\"")))
                .andExpect(content().string(not(containsString("Gym Studio"))));

        mockMvc.perform(get("/favicon.svg")).andExpect(status().isOk());
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isOk());

        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);
        Member sampleMember = new Member(2L, "Hội viên mẫu Bình", "0900000002", LocalDate.of(1998, 6, 20), true,
                LocalDateTime.of(2026, 11, 17, 10, 0), 1L, "Quản lý mẫu");
        when(memberService.search(any(), anyInt())).thenReturn(
                new PageResult<>(List.of(new MemberListItem(sampleMember, "Đang hiệu lực", "1 HĐ", false)), 0, 20, 1));
        when(planService.listAll()).thenReturn(List.of(
                new Plan(1L, "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L, true)));
        mockMvc.perform(get("/members").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"btnNavToggle\"")))
                .andExpect(content().string(containsString("id=\"primarySidebar\"")))
                .andExpect(content().string(containsString("responsive-cards")))
                .andExpect(content().string(containsString("data-label=\"Mã / Hội viên\"")));
        mockMvc.perform(get("/plans").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("responsive-cards")))
                .andExpect(content().string(containsString("data-label=\"Quản lý sửa / trạng thái\"")));
    }

    @Test
    void f03_editFormsPreserveSubmittedInputsAndExcludePasswordsOnError() throws Exception {
        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);

        when(planService.listAll()).thenReturn(List.of(
                new Plan(1L, "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L, true)));
        when(planService.update(eq(1L), any())).thenThrow(
                new GymException(ErrorCode.INVALID_INPUT, "Gói GYM theo ngày không được nhập số buổi PT."));

        mockMvc.perform(post("/plans/1")
                        .with(user(manager))
                        .with(csrf())
                        .param("name", "Gói kiểm định giữ nhập")
                        .param("kind", "GYM")
                        .param("durationDays", "45")
                        .param("sessionsTotal", "1")
                        .param("priceVnd", "350000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Gói GYM theo ngày không được nhập số buổi PT.")))
                .andExpect(content().string(containsString("Gói kiểm định giữ nhập")))
                .andExpect(content().string(containsString("350000")));

        when(trainerService.listForCurrentActor()).thenReturn(List.of(
                new Trainer(1L, "PT mẫu Minh", "Sức mạnh", true)));
        when(availabilityService.listForCurrentActor(any(), any())).thenReturn(List.of(
                new AvailabilitySlot(1L, 1L, "PT mẫu Minh",
                        LocalDateTime.of(2026, 11, 20, 8, 0),
                        LocalDateTime.of(2026, 11, 20, 12, 0))));
        when(trainerService.update(eq(1L), any())).thenThrow(
                new GymException(ErrorCode.INVALID_INPUT, "Chuyên môn PT phải dài từ 2 đến 150 ký tự."));
        mockMvc.perform(post("/trainers/1")
                        .with(user(manager))
                        .with(csrf())
                        .param("fullName", "PT Giữ Nhập Tên")
                        .param("specialty", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("PT Giữ Nhập Tên")));

        when(availabilityService.update(eq(1L), any())).thenThrow(
                new GymException(ErrorCode.AVAILABILITY_CONFLICT, "Khung giờ mới bị chồng lấn."));
        mockMvc.perform(post("/availability/1")
                        .with(user(manager))
                        .with(csrf())
                        .param("date", "2026-11-22")
                        .param("startTime", "09:30")
                        .param("endTime", "13:30"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("2026-11-22")))
                .andExpect(content().string(containsString("09:30")));

        when(equipmentService.listAll(any())).thenReturn(List.of(
                new Equipment(1L, "TM01", "Máy chạy bộ 01", "Khu cardio", EquipmentStatus.AVAILABLE, "OK",
                        LocalDateTime.of(2026, 11, 17, 8, 0), 1L, "Quản lý mẫu")));
        when(equipmentService.update(eq(1L), any())).thenThrow(
                new GymException(ErrorCode.INVALID_INPUT, "Mã thiết bị đã tồn tại."));
        mockMvc.perform(post("/equipment/1")
                        .with(user(manager))
                        .with(csrf())
                        .param("code", "BK01")
                        .param("name", "Tên máy mới giữ lại")
                        .param("location", "Tầng 2")
                        .param("q", "cardio"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Tên máy mới giữ lại")))
                .andExpect(content().string(containsString("Tầng 2")));

        when(userService.listAll()).thenReturn(List.of(
                new UserAccount(1L, "manager", "$2a$12$x", "Quản lý mẫu", Role.MANAGER, null, 0, true, null)));
        when(userService.listAvailableTrainersForUserCreation()).thenReturn(List.of());
        doThrow(new GymException(ErrorCode.INVALID_INPUT, "Tên hiển thị phải dài từ 2 đến 100 ký tự."))
                .when(userService).updateName(eq(1L), any());
        mockMvc.perform(post("/users/1/name")
                        .with(user(manager))
                        .with(csrf())
                        .param("fullName", "Q"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Tên hiển thị phải dài từ 2 đến 100 ký tự.")))
                .andExpect(content().string(containsString("value=\"Q\"")));

        doThrow(new GymException(ErrorCode.INVALID_INPUT, "Mật khẩu phải dài từ 8 đến 72 byte UTF-8."))
                .when(userService).resetPassword(eq(1L), any());
        mockMvc.perform(post("/users/1/reset-password")
                        .with(user(manager))
                        .with(csrf())
                        .param("newPassword", "shortpw"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("8 đến 72 byte UTF-8")))
                .andExpect(content().string(not(containsString("shortpw"))));
    }

    @Test
    void f04_bindingTypeMismatchInvalidEnumAndMissingParamNeverReturnWhitelabel() throws Exception {
        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);
        when(planService.listAll()).thenReturn(List.of(
                new Plan(1L, "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L, true)));
        when(reportService.expiringGym()).thenReturn(List.of());
        when(visitService.listOpenVisits()).thenReturn(List.of());
        when(visitService.search(any(), any(), any(), any(), anyInt())).thenReturn(
                new PageResult<>(List.of(), 0, 20, 0));
        when(memberRepository.findAllBasicForSelect()).thenReturn(List.of());

        mockMvc.perform(post("/plans/1")
                        .with(user(manager))
                        .with(csrf())
                        .param("name", "Gói giữ sau số lẻ")
                        .param("kind", "GYM")
                        .param("durationDays", "1.5")
                        .param("priceVnd", "300000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(containsString("Gói giữ sau số lẻ")))
                .andExpect(content().string(containsString("1.5")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        mockMvc.perform(get("/reports")
                        .with(user(manager))
                        .param("fromDate", "bad")
                        .param("toDate", "2026-11-18"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        mockMvc.perform(get("/visits")
                        .with(user(manager))
                        .param("fromDate", "2026-11-18")
                        .param("toDate", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        mockMvc.perform(post("/equipment/1/status")
                        .with(user(manager))
                        .with(csrf())
                        .param("targetStatus", "INVALID_ENUM")
                        .param("note", "ok"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        mockMvc.perform(post("/plans/1/active")
                        .with(user(manager))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));
    }

    @Test
    void f05_f06_paginationBadInputsAndMissingSingleDateRejectedCleanly() throws Exception {
        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);
        when(memberService.search(any(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        when(visitService.listOpenVisits()).thenReturn(List.of());
        when(memberRepository.findAllBasicForSelect()).thenReturn(List.of());
        when(visitService.search(any(), any(), any(), any(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        when(reportService.expiringGym()).thenReturn(List.of());

        mockMvc.perform(get("/members").with(user(manager)).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        mockMvc.perform(get("/members").with(user(manager)).param("page", "999999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("DMT Gym")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        mockMvc.perform(get("/reports").with(user(manager)).param("fromDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Vui lòng nhập đủ cả Từ ngày và Đến ngày")));

        mockMvc.perform(get("/visits").with(user(manager)).param("fromDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Vui lòng nhập đủ cả Từ ngày và Đến ngày")));
    }

    @Test
    void f08_f12_accessibleLabelsQuotaSummaryConfirmDialogAndPasswordBytesNotice() throws Exception {
        GymUserDetails manager = principal(1L, "manager", Role.MANAGER, null, 0);
        Member sampleMember = new Member(2L, "Hội viên mẫu Bình", "0900000002", LocalDate.of(1998, 6, 20), true,
                LocalDateTime.of(2026, 11, 17, 10, 0), 1L, "Quản lý mẫu");
        Membership samplePtMs = new Membership(7L, 2L, 4L, SaleType.PURCHASE, "00000000-0000-4000-8000-000000000007",
                "PT 4 buổi", PlanKind.PT, 30, 4, 1_200_000L,
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 7),
                LocalDateTime.of(2026, 11, 7, 9, 0), 2L);
        MembershipDetailView detailView = new MembershipDetailView(
                samplePtMs, MembershipStatus.ACTIVE, LocalDate.of(2026, 12, 6),
                QuotaView.compute(4, 1, 2), 7L, "Lễ tân mẫu"
        );

        when(planService.listAll()).thenReturn(List.of(
                new Plan(1L, "Gym 30 ngày", PlanKind.GYM, 30, null, 300_000L, true)));
        when(userService.listAll()).thenReturn(List.of(
                new UserAccount(1L, "manager", "$2a$12$x", "Quản lý mẫu", Role.MANAGER, null, 0, true, null)));
        when(userService.listAvailableTrainersForUserCreation()).thenReturn(List.of());
        when(equipmentService.listAll(any())).thenReturn(List.of(
                new Equipment(1L, "TM01", "Máy chạy bộ 01", "Khu cardio", EquipmentStatus.AVAILABLE, "OK",
                        LocalDateTime.of(2026, 11, 17, 8, 0), 1L, "Quản lý mẫu")));
        when(bookingService.listOverduePending()).thenReturn(List.of());
        when(bookingService.search(any(), any(), any(), any(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        when(trainerService.listForCurrentActor()).thenReturn(List.of(new Trainer(1L, "PT mẫu Minh", "Sức mạnh", true)));
        when(trainerService.listAllActive()).thenReturn(List.of(new Trainer(1L, "PT mẫu Minh", "Sức mạnh", true)));
        when(memberRepository.findAllBasicForSelect()).thenReturn(List.of(sampleMember));
        when(memberRepository.findAllActiveForSelect()).thenReturn(List.of(sampleMember));
        when(availabilityService.listForCurrentActor(any(), any())).thenReturn(List.of());
        when(bookingService.listPtContractsForMember(anyLong())).thenReturn(List.of(detailView));

        mockMvc.perform(get("/plans").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("for=\"plan-name-1\"")))
                .andExpect(content().string(containsString("for=\"plan-days-1\"")))
                .andExpect(content().string(containsString("for=\"plan-price-1\"")));

        mockMvc.perform(get("/equipment").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("for=\"eq-broken-note-1\"")))
                .andExpect(content().string(containsString("for=\"eq-code-1\"")));

        mockMvc.perform(get("/users").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("8–72 byte UTF-8")))
                .andExpect(content().string(containsString("for=\"user-pwd-1\"")));

        mockMvc.perform(get("/pt-bookings").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"ptQuotaSummary\"")))
                .andExpect(content().string(containsString("id=\"ptResolveConfirmDialog\"")));
    }

    @Test
    void tc01_wrongPasswordLockedAccountAndLogoutFlow() throws Exception {
        String hash = passwordEncoder.encode("Gym@12345");
        UserAccount activeManager = new UserAccount(1L, "manager", hash, "Quản lý mẫu", Role.MANAGER, null, 0, true, null);
        UserAccount lockedStaff = new UserAccount(5L, "lockeduser", hash, "Đã khóa", Role.RECEPTIONIST, null, 1, false, null);

        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(activeManager));
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeManager));
        when(userRepository.findByUsername("lockeduser")).thenReturn(Optional.of(lockedStaff));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "manager")
                        .param("password", "WrongPass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "lockeduser")
                        .param("password", "Gym@12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(post("/logout").with(user(new GymUserDetails(activeManager))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
