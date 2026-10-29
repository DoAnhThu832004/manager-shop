package com.thupo.managerShop.giftcard.runner;

import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.giftcard.dto.GiftCardCreateRequest;
import com.thupo.managerShop.giftcard.dto.GiftCardCreatedResponse;
import com.thupo.managerShop.giftcard.repository.GiftCardRepository;
import com.thupo.managerShop.giftcard.service.GiftCardService;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runner khởi tạo dữ liệu mẫu thẻ quà tặng chỉ chạy ở profile "dev".
 * Không seed bằng SQL do mã cần được băm bằng HMAC-SHA256 với secret pepper từ cấu hình.
 * In mã thẻ gốc ra console đúng MỘT LẦN duy nhất khi khởi tạo.
 */
@Component
@Profile("dev")
public class GiftCardDevRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GiftCardDevRunner.class);

    private final GiftCardRepository giftCardRepository;
    private final GiftCardService giftCardService;
    private final UserRepository userRepository;

    public GiftCardDevRunner(GiftCardRepository giftCardRepository,
                             GiftCardService giftCardService,
                             UserRepository userRepository) {
        this.giftCardRepository = giftCardRepository;
        this.giftCardService = giftCardService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (giftCardRepository.count() == 0) {
            Long adminUserId = userRepository.findAll().stream()
                    .findFirst()
                    .map(user -> user.getId())
                    .orElse(null);

            GiftCardCreateRequest request = new GiftCardCreateRequest(
                    null, // Tự động sinh mã 16 ký tự an toàn
                    BigDecimal.valueOf(10000.00),
                    null, // Doesn't expire (vĩnh viễn)
                    null,
                    "Thẻ quà tặng mẫu khởi tạo tự động cho môi trường dev"
            );

            GiftCardCreatedResponse created = giftCardService.createGiftCard(request, adminUserId);

            System.out.println("===============================================================================");
            System.out.println("[DEV-ONLY] Đã khởi tạo thẻ quà tặng mẫu (10,000 VND)");
            System.out.println("[DEV-ONLY] ID: " + created.id() + " | MASKED CODE: " + created.maskedCode());
            System.out.println("[DEV-ONLY] MÃ GỐC DUY NHẤT (PRINTED ONCE): " + created.code());
            System.out.println("===============================================================================");
        }
    }
}
