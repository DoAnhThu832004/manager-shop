package com.thupo.managerShop.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.dto.ChangePasswordRequest;
import com.thupo.managerShop.auth.dto.ForgotPasswordRequest;
import com.thupo.managerShop.auth.dto.LoginRequest;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.dto.RefreshTokenRequest;
import com.thupo.managerShop.auth.dto.ResetPasswordRequest;
import com.thupo.managerShop.auth.entity.PasswordResetToken;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.repository.PasswordResetTokenRepository;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.RoleRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("managershop_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (mysql.isRunning()) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
        }
        // Cấu hình môi trường test
        registry.add("app.security.jwt.secret", () -> "dGhpc0lzQVZlcnlTZWN1cmVKd3RTZWNyZXRLZXlGb3JUZXN0aW5nMTIzNDU2");
        registry.add("app.security.rate-limit.store-type", () -> "mysql");
        registry.add("spring.flyway.enabled", () -> "true");
        // Ngưỡng captcha trong test đặt là 10 để bài test nhập sai 5 lần không bị Captcha chặn ngang
        registry.add("app.security.login.captcha-after-failures", () -> "10");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.thupo.managerShop.auth.service.EmailService emailService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setupData() {
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();

        Role cashierRole = roleRepository.findByName(RoleName.CASHIER).orElseGet(() -> {
            Role r = new Role();
            r.setName(RoleName.CASHIER);
            return roleRepository.save(r);
        });

        Role ownerRole = roleRepository.findByName(RoleName.OWNER).orElseGet(() -> {
            Role r = new Role();
            r.setName(RoleName.OWNER);
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setUsername("cashier_test");
        testUser.setEmail("cashier@shop.com");
        testUser.setFullName("Thu Ngân 01");
        testUser.setPasswordHash(passwordEncoder.encode("Password123"));
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setRoles(Set.of(cashierRole));
        testUser = userRepository.save(testUser);

        adminUser = new User();
        adminUser.setUsername("owner_admin");
        adminUser.setEmail("owner@shop.com");
        adminUser.setFullName("Chủ Cửa Hàng");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass123"));
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRoles(Set.of(ownerRole));
        adminUser = userRepository.save(adminUser);
    }

    @Test
    @DisplayName("Kịch bản 1: Luồng hoàn chỉnh Đăng nhập -> Refresh token rotation -> Đăng xuất")
    void shouldExecuteFullLoginRefreshLogoutFlow() throws Exception {
        // 1. Đăng nhập thành công
        LoginRequest loginRequest = new LoginRequest("cashier_test", "Password123", "POS-Device-1", null);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String accessToken = loginResponse.accessToken();
        String refreshToken = loginResponse.refreshToken();

        // 2. Làm mới token (Refresh Rotation)
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        LoginResponse refreshResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), LoginResponse.class);
        String newRefreshToken = refreshResponse.refreshToken();
        String newAccessToken = refreshResponse.accessToken();

        // 3. Xem danh sách phiên đăng nhập
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Đăng xuất phiên hiện tại
        RefreshTokenRequest logoutRequest = new RefreshTokenRequest(newRefreshToken);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        // 5. Thử refresh lại bằng token vừa đăng xuất -> phải bị từ chối 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kịch bản 2: Nhập sai 5 lần liên tiếp -> Bị khoá tài khoản 423 -> Admin mở khoá thành công")
    void shouldLockAccountAfter5FailedAttemptsAndUnlockByAdmin() throws Exception {
        LoginRequest wrongPassRequest = new LoginRequest("cashier_test", "WrongPassword", "POS-1", null);

        // Thử sai 4 lần -> Trả 401 INVALID_CREDENTIALS
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPassRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }

        // Lần thứ 5 sai -> Tài khoản bị khoá tạm thời (423 Locked)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassRequest)))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // Lần thứ 6 dù nhập ĐÚNG mật khẩu cũng vẫn bị từ chối 423
        LoginRequest correctPassRequest = new LoginRequest("cashier_test", "Password123", "POS-1", null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPassRequest)))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));

        // Admin đăng nhập để lấy token quyền OWNER
        LoginRequest adminLogin = new LoginRequest("owner_admin", "AdminPass123", "AdminPC", null);
        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = objectMapper.readValue(adminResult.getResponse().getContentAsString(), LoginResponse.class).accessToken();

        // Admin gọi API mở khoá tài khoản
        mockMvc.perform(post("/api/v1/auth/admin/users/" + testUser.getId() + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã mở khoá tài khoản thành công"));

        // Sau khi mở khoá: Đăng nhập lại đúng mật khẩu -> Thành công 200 OK
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPassRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Kịch bản 3: Phát hiện tái sử dụng Refresh Token (Reuse Detection) -> Thu hồi cả họ token")
    void shouldRevokeTokenFamilyOnRefreshTokenReuse() throws Exception {
        // 1. Đăng nhập ban đầu
        LoginRequest loginRequest = new LoginRequest("cashier_test", "Password123", "Device-A", null);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenR0 = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class).refreshToken();

        // 2. Refresh lần 1: R0 đổi thành R1 hợp lệ (R0 trở thành USED)
        RefreshTokenRequest refreshReq1 = new RefreshTokenRequest(tokenR0);
        MvcResult refResult1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenR1 = objectMapper.readValue(refResult1.getResponse().getContentAsString(), LoginResponse.class).refreshToken();

        // Giả lập vượt quá Grace Period (5 giây) bằng cách chỉnh usedAt lùi về trước
        String r0Hash = TokenHashUtil.sha256Hex(tokenR0);
        com.thupo.managerShop.auth.entity.RefreshToken r0 = refreshTokenRepository.findByTokenHash(r0Hash)
                .orElseThrow(() -> new AssertionError("Token R0 phải tồn tại trong DB"));
        r0.setUsedAt(java.time.Instant.now().minusSeconds(15));
        refreshTokenRepository.saveAndFlush(r0);
        entityManager.clear();

        // 3. Kẻ tấn công cố tình gửi lại token R0 đã dùng -> Phát hiện TÁI SỬ DỤNG
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSED"));

        // 4. Token mới R1 của người dùng hợp lệ cũng phải bị thu hồi theo (do cả họ family bị huỷ)
        RefreshTokenRequest refreshReq2 = new RefreshTokenRequest(tokenR1);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq2)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Kịch bản 4: Quên mật khẩu hoàn chỉnh -> Đặt lại mật khẩu -> Dùng lại token lần 2 phải lỗi 410")
    void shouldExecuteFullPasswordResetFlowAndFailOnReuse() throws Exception {
        // 1. Yêu cầu quên mật khẩu
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("cashier@shop.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        // Lấy token vừa sinh ra từ DB để mô phỏng link trong email
        PasswordResetToken generatedToken = passwordResetTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(testUser.getId()))
                .findFirst()
                .orElseThrow();

        // Giả lập raw token gửi cho user (chúng ta dùng tokenHash giả lập khớp với bản ghi)
        String rawToken = "rawTokenSentViaEmail1234567890123";
        generatedToken.setTokenHash(TokenHashUtil.sha256Hex(rawToken));
        passwordResetTokenRepository.save(generatedToken);

        // 2. Đặt lại mật khẩu lần 1: Hợp lệ
        ResetPasswordRequest resetReq = new ResetPasswordRequest(rawToken, "NewSecurePassword123");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại với mật khẩu mới."));

        // 3. Đặt lại mật khẩu lần 2 với cùng token đó -> Bị từ chối 410 GONE
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));

        // 4. Đăng nhập bằng mật khẩu mới -> Thành công
        LoginRequest newLoginReq = new LoginRequest("cashier_test", "NewSecurePassword123", "POS-1", null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
