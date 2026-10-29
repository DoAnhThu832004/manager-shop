package com.thupo.managerShop.giftcard.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Cấu hình bảo mật cho thẻ quà tặng (Pepper key dùng cho HMAC-SHA256).
 */
@Component
@ConfigurationProperties(prefix = "app.giftcard")
public class GiftCardProperties {

    public static final String DEFAULT_DEV_PEPPER = "dev-secret-pepper-for-gift-cards-2026";

    private String pepper = DEFAULT_DEV_PEPPER;

    private final Environment environment;

    public GiftCardProperties(Environment environment) {
        this.environment = environment;
    }

    public String getPepper() {
        return pepper;
    }

    public void setPepper(String pepper) {
        this.pepper = pepper;
    }

    @PostConstruct
    public void validatePepperForProduction() {
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        if (isProd) {
            if (pepper == null || pepper.isBlank() || DEFAULT_DEV_PEPPER.equals(pepper)) {
                throw new IllegalStateException(
                        "Lỗi bảo mật nghiêm trọng: Biến môi trường GIFTCARD_CODE_PEPPER phải được cấu hình bí mật và khác giá trị mặc định ở profile prod!");
            }
        }
    }
}
