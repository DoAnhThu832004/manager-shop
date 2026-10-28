package com.thupo.managerShop.auth.config;

import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Toàn bộ ngưỡng bảo mật, chỉnh qua {@code application.yaml} (prefix {@code app.security}). */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    @Min(10)
    private int bcryptStrength = 12;

    @Valid
    private Login login = new Login();

    @Valid
    private IpBlock ipBlock = new IpBlock();

    @Valid
    private Jwt jwt = new Jwt();

    @Valid
    private PasswordReset passwordReset = new PasswordReset();

    @Valid
    private RateLimit rateLimit = new RateLimit();

    @Valid
    private Registration registration = new Registration();

    @Valid
    private Cors cors = new Cors();

    /** Danh sách IP/CIDR của proxy tin cậy; chỉ khi request đến từ đây mới đọc X-Forwarded-For. */
    private List<String> trustedProxies = new ArrayList<>();

    @Getter
    @Setter
    public static class Login {
        @Min(1)
        private int maxFailedAttempts = 5;
        @NotEmpty
        private List<Integer> lockDurationsMinutes = new ArrayList<>(List.of(15, 30, 60, 1440));
        @Min(1)
        private int maxSessionsPerUser = 5;
        @Min(1)
        private int captchaAfterFailures = 3;
    }

    @Getter
    @Setter
    public static class IpBlock {
        @Min(1)
        private int windowMinutes = 10;
        @Min(1)
        private int maxFailuresPerIp = 50;
        @Min(1)
        private int maxDistinctUsernamesPerIp = 10;
        @NotEmpty
        private List<Integer> blockDurationsMinutes = new ArrayList<>(List.of(30, 120, 1440));
        @Min(1)
        private int blockCacheSeconds = 10;
    }

    @Getter
    @Setter
    public static class Jwt {
        /** Secret HS256, tối thiểu 32 byte, đọc từ biến môi trường JWT_SECRET. */
        @NotBlank
        private String secret;
        @NotBlank
        private String issuer = "managershop";
        @Min(1)
        private int accessTtlMinutes = 15;
        @Min(1)
        private int refreshTtlDays = 7;
        @Min(0)
        private int refreshGraceSeconds = 5;
        @Min(1)
        private int userStateCacheSeconds = 30;
    }

    @Getter
    @Setter
    public static class PasswordReset {
        @Min(1)
        private int ttlMinutes = 15;
        @Min(1)
        private int maxPerHourPerEmail = 3;
        @Min(1)
        private int maxPerHourPerIp = 10;
        @Min(1)
        private int historySize = 3;
    }

    @Getter
    @Setter
    public static class RateLimit {
        @Min(1)
        private int loginPerMinutePerIp = 60;
        @Min(1)
        private int registerPerMinutePerIp = 5;
        @Min(0)
        private int loginPerMinutePerUser = 0;
        /** Khi Redis lỗi: true = fallback sang In-Memory rate limit, false = từ chối. */
        private boolean failOpen = true;
    }

    @Getter
    @Setter
    public static class Registration {
        private boolean enabled = true;
        private RoleName defaultRole = RoleName.CASHIER;
        private UserStatus initialStatus = UserStatus.ACTIVE;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        @Min(0)
        private long maxAgeSeconds = 3600;
    }
}
