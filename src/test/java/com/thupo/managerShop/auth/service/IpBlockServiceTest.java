package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.IpBlock;
import com.thupo.managerShop.auth.entity.IpWhitelist;
import com.thupo.managerShop.auth.ratelimit.RateLimitStore;
import com.thupo.managerShop.auth.repository.IpBlockRepository;
import com.thupo.managerShop.auth.repository.IpWhitelistRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpBlockServiceTest {

    @Mock
    private IpBlockRepository ipBlockRepository;
    @Mock
    private IpWhitelistRepository ipWhitelistRepository;
    @Mock
    private RateLimitStore rateLimitStore;
    @Mock
    private AuditService auditService;

    private AppSecurityProperties properties;
    private IpBlockService ipBlockService;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getIpBlock().setWindowMinutes(10);
        properties.getIpBlock().setMaxFailuresPerIp(20);
        properties.getIpBlock().setMaxDistinctUsernamesPerIp(10);
        properties.getIpBlock().setBlockDurationsMinutes(List.of(30, 120, 1440));
        properties.getIpBlock().setBlockCacheSeconds(60);

        ipBlockService = new IpBlockService(
                ipBlockRepository,
                ipWhitelistRepository,
                rateLimitStore,
                auditService,
                properties
        );
    }

    @Test
    @DisplayName("IP trong Whitelist không bao giờ bị chặn kể cả có nhiều lần thất bại")
    void shouldNeverBlockWhitelistedIp() {
        String whitelistedIp = "192.168.1.50";
        IpWhitelist whitelistItem = new IpWhitelist();
        whitelistItem.setIpOrCidr("192.168.1.0/24");
        when(ipWhitelistRepository.findAll()).thenReturn(List.of(whitelistItem));

        ipBlockService.recordFailedAttempt(whitelistedIp, "admin", "User-Agent-1");

        assertFalse(ipBlockService.isBlocked(whitelistedIp));
        verify(ipBlockRepository, never()).save(any());
        verify(rateLimitStore, never()).recordIpFailure(any(), any(), any());
    }

    @Test
    @DisplayName("IP chưa vượt ngưỡng thất bại thì không bị chặn")
    void shouldNotBlockWhenBelowThreshold() {
        String clientIp = "198.51.100.1";
        when(ipWhitelistRepository.findAll()).thenReturn(List.of());
        when(rateLimitStore.getIpFailureCount(eq(clientIp), any(Duration.class))).thenReturn(5L);
        when(rateLimitStore.getIpDistinctUsernameCount(eq(clientIp), any(Duration.class))).thenReturn(2L);

        ipBlockService.recordFailedAttempt(clientIp, "user1", "Agent-1");

        verify(ipBlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("IP vượt quá 20 lần thất bại trong 10 phút -> tự động chặn ở cấp độ 1 (30 phút)")
    void shouldBlockIpWhenExceedingMaxFailures() {
        String attackerIp = "198.51.100.99";
        when(ipWhitelistRepository.findAll()).thenReturn(List.of());
        when(rateLimitStore.getIpFailureCount(eq(attackerIp), any(Duration.class))).thenReturn(20L);
        when(rateLimitStore.getIpDistinctUsernameCount(eq(attackerIp), any(Duration.class))).thenReturn(3L);
        when(ipBlockRepository.findByIpAddress(attackerIp)).thenReturn(Optional.empty());

        ipBlockService.recordFailedAttempt(attackerIp, "targetUser", "BruteForceTool/1.0");

        ArgumentCaptor<IpBlock> captor = ArgumentCaptor.forClass(IpBlock.class);
        verify(ipBlockRepository).save(captor.capture());

        IpBlock saved = captor.getValue();
        assertEquals(attackerIp, saved.getIpAddress());
        assertEquals(1, saved.getBlockLevel());
        assertNotNull(saved.getBlockedUntil());
        assertTrue(saved.getBlockedUntil().isAfter(Instant.now().plus(Duration.ofMinutes(29))));

        // Kiểm tra logic isBlocked sau khi chặn
        assertTrue(ipBlockService.isBlocked(attackerIp));
    }

    @Test
    @DisplayName("IP thử > 10 username khác nhau trong 10 phút (Credential stuffing) -> chặn ngay")
    void shouldBlockIpWhenCredentialStuffingDetected() {
        String attackerIp = "198.51.100.88";
        when(ipWhitelistRepository.findAll()).thenReturn(List.of());
        when(rateLimitStore.getIpFailureCount(eq(attackerIp), any(Duration.class))).thenReturn(11L);
        when(rateLimitStore.getIpDistinctUsernameCount(eq(attackerIp), any(Duration.class))).thenReturn(10L);
        when(ipBlockRepository.findByIpAddress(attackerIp)).thenReturn(Optional.empty());

        ipBlockService.recordFailedAttempt(attackerIp, "user10", "StuffingTool/2.0");

        ArgumentCaptor<IpBlock> captor = ArgumentCaptor.forClass(IpBlock.class);
        verify(ipBlockRepository).save(captor.capture());

        IpBlock saved = captor.getValue();
        assertEquals(1, saved.getBlockLevel());
        assertTrue(saved.getReason().contains("Credential Stuffing"));
    }

    @Test
    @DisplayName("IP tái phạm nhiều lần -> chặn luỹ tiến đến vĩnh viễn (Level 4, blocked_until = null)")
    void shouldEscalateBlockLevelProgressively() {
        String repeatAttackerIp = "198.51.100.77";
        when(ipWhitelistRepository.findAll()).thenReturn(List.of());
        when(rateLimitStore.getIpFailureCount(eq(repeatAttackerIp), any(Duration.class))).thenReturn(25L);

        // Đã từng bị chặn ở cấp 3 (24 giờ)
        IpBlock existingBlock = new IpBlock();
        existingBlock.setIpAddress(repeatAttackerIp);
        existingBlock.setBlockLevel(3);
        when(ipBlockRepository.findByIpAddress(repeatAttackerIp)).thenReturn(Optional.of(existingBlock));

        ipBlockService.recordFailedAttempt(repeatAttackerIp, "victim", "Agent-X");

        ArgumentCaptor<IpBlock> captor = ArgumentCaptor.forClass(IpBlock.class);
        verify(ipBlockRepository).save(captor.capture());

        IpBlock escalated = captor.getValue();
        assertEquals(4, escalated.getBlockLevel());
        // Cấp 4 vượt quá mảng cấu hình 3 phần tử -> Chặn vĩnh viễn (blocked_until = null)
        assertNull(escalated.getBlockedUntil());
    }

    @Test
    @DisplayName("Admin mở chặn IP: xóa bản ghi DB, invalidate cache và reset bộ đếm")
    void shouldUnblockIpSuccessfully() {
        String ipToUnblock = "198.51.100.66";

        ipBlockService.unblockIp(ipToUnblock, "admin_user", "AdminBrowser/1.0");

        verify(ipBlockRepository).deleteByIpAddress(ipToUnblock);
        verify(rateLimitStore).resetIp(ipToUnblock);
        assertFalse(ipBlockService.isBlocked(ipToUnblock));
    }
}
