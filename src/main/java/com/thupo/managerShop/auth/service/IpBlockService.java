package com.thupo.managerShop.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.IpBlock;
import com.thupo.managerShop.auth.entity.IpWhitelist;
import com.thupo.managerShop.auth.ratelimit.RateLimitStore;
import com.thupo.managerShop.auth.repository.IpBlockRepository;
import com.thupo.managerShop.auth.repository.IpWhitelistRepository;
import com.thupo.managerShop.auth.security.CidrMatcher;
import com.thupo.managerShop.auth.security.IpAccessPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý chính sách truy cập IP, chặn IP tự động khi phát hiện tấn công
 * Brute-force / Credential Stuffing / Password Spraying và mở chặn bởi Admin.
 */
@Service
public class IpBlockService implements IpAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(IpBlockService.class);

    private final IpBlockRepository ipBlockRepository;
    private final IpWhitelistRepository ipWhitelistRepository;
    private final RateLimitStore rateLimitStore;
    private final AuditService auditService;
    private final AppSecurityProperties properties;

    // Cache trạng thái IP bị chặn trong bộ nhớ ngắn hạn để IpBlockFilter không query MySQL liên tục
    private final Cache<String, Optional<Instant>> blockCache;
    // Cache danh sách IP/CIDR Whitelist trong 5 phút
    private final Cache<String, List<String>> whitelistCache;

    public IpBlockService(IpBlockRepository ipBlockRepository,
                          IpWhitelistRepository ipWhitelistRepository,
                          RateLimitStore rateLimitStore,
                          AuditService auditService,
                          AppSecurityProperties properties) {
        this.ipBlockRepository = ipBlockRepository;
        this.ipWhitelistRepository = ipWhitelistRepository;
        this.rateLimitStore = rateLimitStore;
        this.auditService = auditService;
        this.properties = properties;

        this.blockCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getIpBlock().getBlockCacheSeconds()))
                .maximumSize(100_000)
                .build();

        this.whitelistCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1)
                .build();
    }

    @Override
    public boolean isBlocked(String clientIp) {
        if (isWhitelisted(clientIp)) {
            return false;
        }

        Optional<Instant> blockedUntilOpt = blockCache.get(clientIp, this::loadBlockedUntilFromDb);
        if (blockedUntilOpt == null || blockedUntilOpt.isEmpty()) {
            return false;
        }

        Instant blockedUntil = blockedUntilOpt.get();
        // NULL nghĩa là bị chặn vĩnh viễn (level 4+)
        if (blockedUntil.equals(Instant.MAX)) {
            return true;
        }

        if (blockedUntil.isAfter(Instant.now())) {
            return true;
        } else {
            // Đã hết hạn block
            blockCache.invalidate(clientIp);
            return false;
        }
    }

    @Override
    public Long retryAfterSeconds(String clientIp) {
        Optional<Instant> blockedUntilOpt = blockCache.getIfPresent(clientIp);
        if (blockedUntilOpt == null || blockedUntilOpt.isEmpty()) {
            return null;
        }
        Instant blockedUntil = blockedUntilOpt.get();
        if (blockedUntil.equals(Instant.MAX)) {
            return null;
        }
        long diff = Duration.between(Instant.now(), blockedUntil).toSeconds();
        return Math.max(0, diff);
    }

    /**
     * Kiểm tra xem IP có thuộc danh sách IP Whitelist nội bộ cửa hàng không.
     */
    public boolean isWhitelisted(String clientIp) {
        List<String> whitelistPatterns = whitelistCache.get("all", key ->
                ipWhitelistRepository.findAll().stream().map(IpWhitelist::getIpOrCidr).toList()
        );

        if (whitelistPatterns == null || whitelistPatterns.isEmpty()) {
            return false;
        }

        for (String pattern : whitelistPatterns) {
            if (CidrMatcher.matches(pattern, clientIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ghi nhận 1 lần thất bại từ IP và kiểm tra ngưỡng để tự động kích hoạt chặn IP.
     */
    @Transactional
    public void recordFailedAttempt(String ip, String username, String userAgent) {
        if (isWhitelisted(ip)) {
            return;
        }

        Duration window = Duration.ofMinutes(properties.getIpBlock().getWindowMinutes());
        rateLimitStore.recordIpFailure(ip, username, window);

        long failureCount = rateLimitStore.getIpFailureCount(ip, window);
        long distinctUsers = rateLimitStore.getIpDistinctUsernameCount(ip, window);

        int maxFailures = properties.getIpBlock().getMaxFailuresPerIp();
        int maxDistinct = properties.getIpBlock().getMaxDistinctUsernamesPerIp();

        boolean triggerBlock = false;
        String reason = null;

        if (failureCount >= maxFailures) {
            triggerBlock = true;
            reason = String.format("Vượt quá %d lần đăng nhập thất bại trong %d phút", failureCount, properties.getIpBlock().getWindowMinutes());
        } else if (distinctUsers >= maxDistinct) {
            triggerBlock = true;
            reason = String.format("Thử đăng nhập %d tài khoản khác nhau trong %d phút (Credential Stuffing)", distinctUsers, properties.getIpBlock().getWindowMinutes());
        }

        if (triggerBlock) {
            applyBlock(ip, reason, userAgent);
        }
    }

    /**
     * Thực hiện logic chặn luỹ tiến:
     * Lần 1 = 30 phút, Lần 2 = 2 giờ, Lần 3 = 24 giờ, Lần 4 trở đi = vĩnh viễn (NULL).
     */
    private void applyBlock(String ip, String reason, String userAgent) {
        Optional<IpBlock> existingOpt = ipBlockRepository.findByIpAddress(ip);
        int currentLevel = existingOpt.map(IpBlock::getBlockLevel).orElse(0);
        int nextLevel = currentLevel + 1;

        List<Integer> durations = properties.getIpBlock().getBlockDurationsMinutes();
        Instant blockedUntil = null;
        if (nextLevel <= durations.size()) {
            int minutes = durations.get(nextLevel - 1);
            blockedUntil = Instant.now().plus(Duration.ofMinutes(minutes));
        } // Nếu vượt quá danh sách -> blockedUntil = null (vĩnh viễn)

        IpBlock ipBlock = existingOpt.orElseGet(IpBlock::new);
        ipBlock.setIpAddress(ip);
        ipBlock.setReason(reason);
        ipBlock.setBlockLevel(nextLevel);
        ipBlock.setBlockedUntil(blockedUntil);
        ipBlock.setCreatedBy("SYSTEM_AUTO");

        ipBlockRepository.save(ipBlock);

        // Cập nhật ngay vào cache để IpBlockFilter chặn ngay lập tức
        blockCache.put(ip, Optional.of(blockedUntil != null ? blockedUntil : Instant.MAX));

        auditService.record(null, AuditEventType.IP_BLOCKED, AuditSeverity.HIGH, ip, userAgent, Map.of(
                "reason", reason,
                "blockLevel", nextLevel,
                "blockedUntil", blockedUntil != null ? blockedUntil.toString() : "PERMANENT"
        ));

        log.warn("ĐÃ CHẶN IP [{}] - Cấp độ: {} - Thời hạn: {} - Lý do: {}",
                ip, nextLevel, blockedUntil != null ? blockedUntil : "Vĩnh viễn", reason);
    }

    /**
     * Mở chặn IP (Admin thao tác).
     */
    @Transactional
    public void unblockIp(String ip, String adminUsername, String userAgent) {
        ipBlockRepository.deleteByIpAddress(ip);
        blockCache.invalidate(ip);
        rateLimitStore.resetIp(ip);

        auditService.record(null, AuditEventType.IP_UNBLOCKED, AuditSeverity.INFO, ip, userAgent, Map.of(
                "unblockedBy", adminUsername
        ));
        log.info("Admin [{}] đã mở chặn IP [{}]", adminUsername, ip);
    }

    private Optional<Instant> loadBlockedUntilFromDb(String ip) {
        return ipBlockRepository.findByIpAddress(ip)
                .filter(b -> b.isActive(Instant.now()))
                .map(b -> b.getBlockedUntil() != null ? b.getBlockedUntil() : Instant.MAX);
    }
}
