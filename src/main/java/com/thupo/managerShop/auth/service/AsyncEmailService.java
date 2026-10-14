package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AsyncConfig;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Triển khai gửi email bất đồng bộ qua JavaMailSender.
 * Các ngoại lệ khi gửi mail được bắt và log lại, tuyệt đối không làm gián đoạn luồng nghiệp vụ.
 */
@Service
public class AsyncEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(AsyncEmailService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;

    public AsyncEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async(AsyncConfig.AUTH_TASK_EXECUTOR)
    @Override
    public void sendAccountLockedAlert(String recipientEmail, String username, Instant lockUntil, String ip) {
        try {
            String formattedTime = lockUntil != null ? TIME_FORMATTER.format(lockUntil) : "vĩnh viễn";
            String content = String.format(
                    "Xin chào %s,\n\n"
                            + "Tài khoản của bạn vừa bị tạm khóa đến %s do nhập sai mật khẩu nhiều lần từ IP %s.\n"
                            + "Nếu không phải bạn thực hiện, vui lòng liên hệ ngay với người quản lý cửa hàng.\n\n"
                            + "Trân trọng,\nHệ thống Quản lý Cửa hàng",
                    username, formattedTime, ip
            );

            sendSimpleMail(recipientEmail, "[CẢNH BÁO BẢO MẬT] Tài khoản bị khóa tạm thời", content);
        } catch (Exception e) {
            log.warn("Không thể gửi email cảnh báo khóa tài khoản tới [{}]: {}", recipientEmail, e.getMessage());
        }
    }

    @Async(AsyncConfig.AUTH_TASK_EXECUTOR)
    @Override
    public void sendNewDeviceLoginAlert(String recipientEmail, String username, String ip, String deviceInfo) {
        try {
            String content = String.format(
                    "Xin chào %s,\n\n"
                            + "Tài khoản của bạn vừa được đăng nhập thành công từ thiết bị mới:\n"
                            + "- IP: %s\n"
                            + "- Thiết bị: %s\n"
                            + "- Thời gian: %s\n\n"
                            + "Nếu không phải bạn, hãy đổi mật khẩu ngay lập tức hoặc liên hệ quản trị viên.\n\n"
                            + "Trân trọng,\nHệ thống Quản lý Cửa hàng",
                    username, ip, deviceInfo != null ? deviceInfo : "Không xác định", TIME_FORMATTER.format(Instant.now())
            );

            sendSimpleMail(recipientEmail, "[BẢO MẬT] Phát hiện đăng nhập từ thiết bị mới", content);
        } catch (Exception e) {
            log.warn("Không thể gửi email thông báo thiết bị mới tới [{}]: {}", recipientEmail, e.getMessage());
        }
    }

    @Async(AsyncConfig.AUTH_TASK_EXECUTOR)
    @Override
    public void sendPasswordResetLink(String recipientEmail, String username, String resetToken) {
        try {
            String content = String.format(
                    "Xin chào %s,\n\n"
                            + "Bạn nhận được email này vì có yêu cầu đặt lại mật khẩu cho tài khoản của bạn.\n"
                            + "Mã xác nhận / Token đặt lại mật khẩu của bạn là: %s\n\n"
                            + "Mã này có hiệu lực trong vòng 15 phút và chỉ được dùng 1 lần.\n"
                            + "Nếu bạn không yêu cầu, vui lòng bỏ qua email này.\n\n"
                            + "Trân trọng,\nHệ thống Quản lý Cửa hàng",
                    username, resetToken
            );

            sendSimpleMail(recipientEmail, "[BẢO MẬT] Yêu cầu đặt lại mật khẩu", content);
        } catch (Exception e) {
            log.warn("Không thể gửi email đặt lại mật khẩu tới [{}]: {}", recipientEmail, e.getMessage());
        }
    }

    @Async(AsyncConfig.AUTH_TASK_EXECUTOR)
    @Override
    public void sendPasswordChangedAlert(String recipientEmail, String username, String ip) {
        try {
            String content = String.format(
                    "Xin chào %s,\n\n"
                            + "Mật khẩu cho tài khoản của bạn vừa được thay đổi thành công vào lúc %s từ IP %s.\n"
                            + "Nếu bạn không thực hiện thay đổi này, hãy liên hệ ngay với người quản trị.\n\n"
                            + "Trân trọng,\nHệ thống Quản lý Cửa hàng",
                    username, TIME_FORMATTER.format(Instant.now()), ip
            );

            sendSimpleMail(recipientEmail, "[BẢO MẬT] Mật khẩu tài khoản đã được thay đổi", content);
        } catch (Exception e) {
            log.warn("Không thể gửi email xác nhận đổi mật khẩu tới [{}]: {}", recipientEmail, e.getMessage());
        }
    }

    private void sendSimpleMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
