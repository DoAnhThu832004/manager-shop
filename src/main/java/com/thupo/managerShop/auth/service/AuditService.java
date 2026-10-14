package com.thupo.managerShop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AsyncConfig;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditLog;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.repository.AuditLogRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service ghi log sự kiện kiểm toán bảo mật (Audit log) bất đồng bộ.
 * Không làm chậm hay gián đoạn luồng nghiệp vụ chính khi có lỗi lưu trữ.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ghi nhật ký sự kiện bảo mật.
     *
     * @param userId     ID tài khoản (có thể null nếu chưa xác thực được)
     * @param eventType  Loại sự kiện
     * @param severity   Mức độ cảnh báo (INFO, WARN, HIGH)
     * @param ip         IP client
     * @param userAgent  User-Agent trình duyệt/thiết bị
     * @param detailsMap Thông tin chi tiết bổ sung (tuyệt đối không truyền password, token, secret)
     */
    @Async(AsyncConfig.AUTH_TASK_EXECUTOR)
    public void record(Long userId, AuditEventType eventType, AuditSeverity severity,
                       String ip, String userAgent, Map<String, Object> detailsMap) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setEventType(eventType);
            auditLog.setSeverity(severity != null ? severity : AuditSeverity.INFO);
            auditLog.setIpAddress(ip);
            auditLog.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);

            if (detailsMap != null && !detailsMap.isEmpty()) {
                auditLog.setDetail(objectMapper.writeValueAsString(detailsMap));
            }

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Không thể lưu Audit Log [{}]: {}", eventType, e.getMessage());
        }
    }
}
