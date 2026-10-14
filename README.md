# Module Đăng Nhập & Xác Thực Quản Lý Cửa Hàng (Spring Boot 3 + MySQL + Redis)

Module Authentication chuẩn **Production-Grade**, thiết kế chuyên biệt cho hệ thống bán hàng đa chi nhánh chịu tải cao với nhiều người dùng đăng nhập đồng thời (Chủ cửa hàng, Quản lý, Thu ngân, Nhân viên kho).

---

## 1. Công nghệ sử dụng
- **Ngôn ngữ & Framework**: Java 17, Spring Boot 3.4.5, Spring Security 6, Spring Data JPA (Hibernate).
- **Cơ sở dữ liệu**: MySQL 8 (InnoDB, `utf8mb4_0900_ai_ci`), Schema migration tự động bằng **Flyway**.
- **Bộ đếm phân tán & Rate Limit**: Redis (với `RedisRateLimitStore` mặc định cho môi trường multi-instance), hỗ trợ cơ chế **Fail-Open** khi Redis gặp sự cố để đảm bảo tính sẵn sàng.
- **Token Security**: JWT (HS256) làm Access Token (15 phút), Refresh Token ngẫu nhiên (chỉ lưu hash SHA-256 trong database) với cơ chế **Token Rotation**, **Grace Period** và **Reuse Detection**.
- **Đồng bộ Job nhiều node**: **ShedLock** (`JdbcTemplateLockProvider` trên MySQL) bảo vệ các Scheduled Jobs dọn dẹp chạy an toàn trên một node duy nhất.
- **Bảo mật mật khẩu**: **BCrypt** độ mạnh 12 (configurable).

---

## 2. Biến môi trường cần thiết

Trước khi khởi động ứng dụng, hãy thiết lập các biến môi trường sau:

| Biến môi trường | Bắt buộc | Giá trị mẫu | Mô tả |
|---|---|---|---|
| `JWT_SECRET` | **Có** | `c2VjdXJla2V5MTIzNDU2Nzg5MGFiY2RlZjEyMzQ1Njc4OTBhYmNkZWY=` | Chuỗi bí mật ký HMAC-SHA256 (tối thiểu 32 byte), **không bao giờ hardcode** |
| `DB_URL` | Không | `jdbc:mysql://localhost:3306/managershop?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC` | Đường dẫn kết nối MySQL |
| `DB_USERNAME` | Không | `root` | Tài khoản MySQL |
| `DB_PASSWORD` | Không | `SecretPassword123` | Mật khẩu MySQL |
| `REDIS_HOST` | Không | `localhost` | Địa chỉ Redis Server |
| `REDIS_PORT` | Không | `6379` | Cổng kết nối Redis |
| `REDIS_PASSWORD` | Không | `""` | Mật khẩu Redis nếu có |
| `SMTP_HOST` | Không | `smtp.gmail.com` | Máy chủ gửi email |
| `SMTP_PORT` | Không | `587` | Cổng SMTP |
| `SMTP_USERNAME` | Không | `notification@shop.com` | Email gửi thông báo |
| `SMTP_PASSWORD` | Không | `app-password` | Mật khẩu ứng dụng SMTP |

---

## 3. Hướng dẫn cài đặt & Khởi động

### 3.1. Khởi chạy cơ sở dữ liệu (MySQL & Redis)
Nếu sử dụng Docker, bạn có thể chạy nhanh:
```bash
docker run -d --name shop-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=managershop mysql:8.0
docker run -d --name shop-redis -p 6379:6379 redis:7-alpine
```

### 3.2. Chạy ứng dụng
Trên Windows PowerShell:
```powershell
$env:JWT_SECRET="dGhpc0lzQVZlcnlTZWN1cmVKd3RTZWNyZXRLZXlGb3JUZXN0aW5nMTIzNDU2"
.\mvnw.cmd spring-boot:run
```

### 3.3. Chạy kiểm thử (Unit Tests)
```powershell
.\mvnw.cmd test "-Dtest=ClientIpResolverTest,IpBlockServiceTest,LoginServiceTest,RefreshTokenServiceTest,PasswordResetServiceTest,AuthConcurrencyTest"
```

---

## 4. Hướng dẫn sử dụng API (Ví dụ `curl`)

Tất cả các API xác thực đều có tiền tố `/api/v1/auth`. Định dạng lỗi trả về thống nhất:
```json
{
  "timestamp": "2026-10-05T10:20:00Z",
  "status": 401,
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Tên đăng nhập hoặc mật khẩu không đúng",
  "retryAfterSeconds": null,
  "path": "/api/v1/auth/login"
}
```

### 4.1. Đăng nhập (`POST /api/v1/auth/login`)
Chấp nhận tên đăng nhập (`username`) hoặc `email`.
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "identifier": "thungan01",
    "password": "Password123@",
    "deviceInfo": "POS-Quay-1"
  }'
```
**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "g8Hq2L9P-xY4zW1kM0vR7sT5uN2qE8wI3oA6dC9mB1f",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "roles": ["CASHIER"],
  "captchaRequired": false
}
```

### 4.2. Làm mới Access Token (`POST /api/v1/auth/refresh`)
Cơ chế **Token Rotation**: Trả về Access Token mới và Refresh Token mới.
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "g8Hq2L9P-xY4zW1kM0vR7sT5uN2qE8wI3oA6dC9mB1f"
  }'
```

### 4.3. Đăng xuất phiên hiện tại (`POST /api/v1/auth/logout`)
Thu hồi token family của phiên tương ứng (yêu cầu Access Token).
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "g8Hq2L9P-xY4zW1kM0vR7sT5uN2qE8wI3oA6dC9mB1f"
  }'
```

### 4.4. Đăng xuất tất cả thiết bị (`POST /api/v1/auth/logout-all`)
Thu hồi toàn bộ Refresh Token của user và tăng `token_version` để vô hiệu hóa tức thì mọi Access Token đang còn hạn trên các thiết bị khác.
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout-all \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 4.5. Xem danh sách phiên đăng nhập (`GET /api/v1/auth/sessions`)
```bash
curl -X GET http://localhost:8080/api/v1/auth/sessions \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 4.6. Thu hồi 1 phiên đăng nhập (`DELETE /api/v1/auth/sessions/{id}`)
```bash
curl -X DELETE http://localhost:8080/api/v1/auth/sessions/12 \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 4.7. Quên mật khẩu (`POST /api/v1/auth/forgot-password`)
Luôn trả về 200 OK với thông báo chung dù email có tồn tại hay không (chống User Enumeration).
```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "thungan01@shop.com"
  }'
```

### 4.8. Đặt lại mật khẩu (`POST /api/v1/auth/reset-password`)
Đặt lại mật khẩu bằng token nhận từ email. Token dùng 1 lần, hết hạn sau 15 phút. Không được trùng 3 mật khẩu gần nhất.
```bash
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "43_chars_secure_random_token_from_email",
    "newPassword": "NewSecurePassword123@"
  }'
```

### 4.9. Đổi mật khẩu (`POST /api/v1/auth/change-password`)
Dành cho người dùng đang đăng nhập.
```bash
curl -X POST http://localhost:8080/api/v1/auth/change-password \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "OldPassword123@",
    "newPassword": "BrandNewPassword123@"
  }'
```

### 4.10. Admin: Mở khoá tài khoản (`POST /api/v1/auth/admin/users/{id}/unlock`)
Quyền: `OWNER` hoặc `MANAGER`.
```bash
curl -X POST http://localhost:8080/api/v1/auth/admin/users/5/unlock \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

### 4.11. Admin: Gỡ chặn IP (`DELETE /api/v1/auth/admin/ip-blocks/{ip}`)
Quyền: `OWNER` hoặc `MANAGER`.
```bash
curl -X DELETE http://localhost:8080/api/v1/auth/admin/ip-blocks/198.51.100.99 \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

### 4.12. Admin: Xem nhật ký đăng nhập (`GET /api/v1/auth/admin/login-attempts`)
Hỗ trợ phân trang (`page`, `size`) và lọc theo `ip`, `username`, `success`, `from`, `to`.
```bash
curl -X GET "http://localhost:8080/api/v1/auth/admin/login-attempts?username=thungan01&success=false&page=0&size=10" \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

---

## 5. Các quyết định thiết kế kiến trúc quan trọng

1. **Phân tầng lưu trữ bộ đếm chống Race Condition:**
   - Bộ đếm sai mật khẩu theo tài khoản: Lưu tại MySQL với câu lệnh `UPDATE ... SET failed_login_count = failed_login_count + 1` nguyên tử, không dùng đọc-sửa-ghi qua Entity nhằm ngăn chặn triệt để hiện tượng mất cập nhật khi 50+ request thử sai đồng thời.
   - Bộ đếm tần suất và theo dõi IP: Lưu tại Redis với TTL tự động để xử lý hàng nghìn request/giây mà không gây quá tải MySQL.
2. **Bảo vệ CPU BCrypt bằng Rate Limit & IpBlockFilter:**
   - Quá trình băm BCrypt độ mạnh 12 tốn khoảng 80–120ms CPU trên mỗi request. Kẻ tấn công có thể spam request sai để gây cạn kiệt CPU máy chủ.
   - Vì vậy, `IpBlockFilter` và tầng kiểm tra Rate Limit được kích hoạt **ngay từ đầu request, trước khi chạm tới cơ sở dữ liệu và trước khi chạy thuật toán BCrypt**.
3. **Phòng chống dò tài khoản (User Enumeration & Timing Attack):**
   - Sai username hay sai password đều trả về cùng một mã lỗi `401 AUTH_INVALID_CREDENTIALS` và cùng một thông điệp tiếng Việt.
   - Khi username không tồn tại trong hệ thống, hệ thống vẫn kích hoạt phép so sánh **Dummy Hash** chuẩn BCrypt để thời gian phản hồi giữa user tồn tại và không tồn tại là tương đương nhau. Tuyệt đối không dùng `Thread.sleep()` gây cạn kiệt thread pool.
4. **Refresh Token Rotation & Reuse Detection:**
   - Mỗi lần refresh, một token mới được sinh ra và token cũ được đánh dấu là `USED`.
   - Nếu token `USED` bị gửi lại sau `Grace Period` 5 giây $\rightarrow$ Phát hiện hành vi token bị đánh cắp $\rightarrow$ Thu hồi toàn bộ họ token (`family_id`) ngay lập tức và ghi log kiểm toán mức `HIGH`.

---

## 6. Rủi ro còn lại & Hướng nâng cấp trong thực tế

Dù hệ thống đã đạt tiêu chuẩn Production-Grade cho ứng dụng bán hàng, trong môi trường doanh nghiệp quy mô lớn, các cải tiến sau được khuyến nghị:

1. **Xác thực 2 yếu tố (2FA / MFA qua TOTP):**
   - Tích hợp chuẩn RFC 6238 (Google Authenticator / Microsoft Authenticator) cho các tài khoản có quyền hạn cao như `OWNER` và `MANAGER`.
   - Thu ngân đầu ca có thể dùng mã PIN nhanh kết hợp với thẻ từ POS.
2. **Device Fingerprinting chuyên sâu:**
   - Hiện tại hệ thống phát hiện thiết bị mới qua sự kết hợp của `IP Address` + `User-Agent` + `deviceInfo`.
   - Hướng nâng cấp: Tạo chuỗi fingerprint client-side dựa trên Canvas fingerprinting, WebGL renderer, độ phân giải màn hình và AudioContext để nhận diện chính xác thiết bị ngay cả khi IP thay đổi (4G/Wi-Fi).
3. **Đăng nhập xã hội / SSO:**
   - Hỗ trợ Google OAuth2 / OpenID Connect để chủ shop đăng nhập thuận tiện mà không cần quản lý mật khẩu riêng.
4. **Tường lửa ứng dụng Web (WAF) & Cloudflare:**
   - Trong môi trường Internet mở, nên đặt hệ thống đằng sau Cloudflare hoặc AWS WAF để ngăn chặn tấn công DDoS L3/L4/L7 trước khi traffic chạm tới Spring Boot.
5. **Hardware Security Module (HSM) / Key Management Service (KMS):**
   - Chuyển từ ký HS256 đối xứng sang **RS256** (RSA Asymmetric) hoặc **ES256** (Elliptic Curve), với private key được lưu an toàn trong AWS KMS hoặc HashiCorp Vault.
