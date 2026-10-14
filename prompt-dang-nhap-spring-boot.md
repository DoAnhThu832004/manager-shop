# PROMPT: Module Đăng nhập & Xác thực cho app cửa hàng (Spring Boot + MySQL)

> Copy toàn bộ nội dung bên dưới (từ "## VAI TRÒ") đưa cho AI. Nên yêu cầu AI làm theo từng GIAI ĐOẠN (mục 12) để code không bị cắt giữa chừng.

---

## VAI TRÒ

Bạn là Senior Backend Engineer (10+ năm kinh nghiệm) chuyên về bảo mật và hệ thống chịu tải cao. Hãy thiết kế và viết **module Authentication hoàn chỉnh, production-grade** cho hệ thống quản lý bán hàng của cửa hàng có **nhiều người dùng đăng nhập đồng thời** (chủ cửa hàng, quản lý, thu ngân, nhân viên kho, ở nhiều chi nhánh).

## 1. CÔNG NGHỆ BẮT BUỘC

- **Hiện trạng project (đã có, không tạo lại):** Maven, Java 17, package gốc `com.thupo.managerShop`, `application.yaml` mới chỉ có `spring.application.name`. `pom.xml` hiện có: `data-jpa`, `flyway` + `flyway-mysql`, `mail`, `validation`, `web`, `mysql-connector-j`. **Chưa có**: `spring-boot-starter-security`, thư viện JWT (ví dụ `jjwt`), Caffeine, Lombok, Testcontainers, ShedLock. Mục tiêu là **Spring Boot 3.x**: hãy liệt kê chính xác dependency cần **bổ sung** và các thay đổi cần sửa trong `pom.xml` (ví dụ đổi parent về 3.x, bỏ `spring-cloud-gateway-server-webmvc` nếu không cần, dùng starter tên chuẩn của Boot 3 như `spring-boot-starter-web`).
- Java 17, Spring Boot 3.x, Spring Security 6, Spring Data JPA (Hibernate)
- MySQL 8 (InnoDB, utf8mb4), migration bằng **Flyway**
- JWT (access token) + Refresh Token lưu DB
- Mã hoá mật khẩu: **BCrypt strength 12** (có thể đổi sang Argon2 qua config)
- Redis: **tuỳ chọn** cho rate limit/blocklist. Hãy thiết kế interface (`RateLimitStore`) với 2 implementation: `MySqlRateLimitStore` (mặc định, không cần Redis) và `RedisRateLimitStore` (bật bằng profile/property). Giải thích khi nào nên dùng cái nào.
- Gửi email: `JavaMailSender` (qua interface `EmailService` để dễ thay thế), gửi **bất đồng bộ** (`@Async`)
- Build: Maven. Test: JUnit 5, Mockito, Testcontainers (MySQL), MockMvc
- Kiến trúc phân tầng rõ ràng: `controller → service → repository`, DTO tách khỏi Entity (dùng record hoặc Lombok), không trả Entity ra ngoài API
- Package gốc: `com.thupo.managerShop` (đặt module này trong `com.thupo.managerShop.auth`)

## 2. CHỨC NĂNG CẦN CÓ

1. Đăng nhập bằng `username` hoặc `email` + mật khẩu
2. Đăng xuất (thu hồi refresh token hiện tại) và đăng xuất tất cả thiết bị
3. Refresh token với cơ chế **rotation** + **phát hiện tái sử dụng (reuse detection)**
4. Quên mật khẩu / đặt lại mật khẩu qua email
5. Đổi mật khẩu (khi đã đăng nhập)
6. Khoá tài khoản tạm thời khi nhập sai nhiều lần
7. Chặn IP khi có hành vi tấn công
8. Phân quyền RBAC: `OWNER`, `MANAGER`, `CASHIER`, `WAREHOUSE` (dùng `@PreAuthorize`)
9. Quản lý phiên/thiết bị đăng nhập (xem danh sách, thu hồi từng phiên)
10. Audit log cho mọi sự kiện bảo mật
11. Admin API: mở khoá tài khoản, bỏ chặn IP, xem log đăng nhập

## 3. CÁC TÌNH HUỐNG THỰC TẾ PHẢI XỬ LÝ

### 3.1. Nhiều người dùng đăng nhập đồng thời
- Thu ngân đầu ca đăng nhập cùng lúc (50–200 request/giây): endpoint login phải **stateless**, không giữ lock lâu.
- Cập nhật bộ đếm sai mật khẩu phải **atomic** (dùng `UPDATE ... SET failed_count = failed_count + 1` hoặc optimistic lock `@Version`), tránh race condition khi 2 request sai mật khẩu đến cùng lúc.
- Thêm index phù hợp, dùng connection pool HikariCP có cấu hình hợp lý (nêu rõ giá trị đề xuất).
- Cùng một tài khoản đăng nhập trên nhiều thiết bị: cho phép, giới hạn tối đa N phiên (cấu hình, mặc định 5), vượt quá thì thu hồi phiên cũ nhất.
- Cập nhật `last_login_at` không được làm chậm luồng login (làm async hoặc gộp batch).
- **Phân tách nơi lưu bộ đếm theo mức độ cần chính xác:**
  - Bộ đếm sai mật khẩu và trạng thái khoá **theo tài khoản**: giữ ở **MySQL** (chính xác, đồng bộ giữa nhiều instance).
  - Bộ đếm **theo IP** (số lần sai, số username khác nhau, rate limit) và bảng `login_attempts`: **không ghi MySQL trực tiếp trên từng request**. Dùng Redis cho bộ đếm (hoặc `RateLimitStore` ở mục 1), và ghi `login_attempts` theo **lô bất đồng bộ** (queue trong bộ nhớ, flush mỗi vài giây hoặc khi đủ N bản ghi).
  - Nếu chạy nhiều instance mà không có Redis, không dùng Caffeine làm nguồn đếm chính (mỗi instance đếm riêng sẽ làm ngưỡng bị lệch). Nêu rõ trade-off này và hậu quả khi app restart (mất bộ đếm chưa flush).
  - Lưu ý điểm nghẽn thật của endpoint login thường là CPU của BCrypt (strength 12), không phải MySQL: chặn IP và rate limit phải chạy **trước** bước băm mật khẩu.

### 3.2. Hacker dò mật khẩu (Brute-force) vào 1 tài khoản
- Sai **5 lần liên tiếp** → khoá tài khoản **15 phút**. Khoá luỹ tiến: lần khoá thứ 2 = 30 phút, thứ 3 = 1 giờ, thứ 4 trở đi = 24 giờ (cấu hình được).
- Đăng nhập đúng thì reset bộ đếm.
- Trong lúc bị khoá, **kể cả nhập đúng mật khẩu cũng bị từ chối**; trả về thời gian còn lại (`retryAfterSeconds`) và header `Retry-After`.
- Gửi email cảnh báo cho chủ tài khoản khi bị khoá ("Có người đang cố đăng nhập vào tài khoản của bạn").
- Không để kẻ tấn công **khoá tài khoản của người khác để gây DoS** một cách dễ dàng: kết hợp đếm theo cặp (username + IP), mô tả rõ trade-off và cách bạn chọn.

### 3.3. Hacker thử 1 mật khẩu trên nhiều tài khoản (Credential stuffing / Password spraying)
- Đếm theo **IP**: một IP có > 20 lần đăng nhập thất bại trong 10 phút (bất kể username) → chặn IP.
- Phát hiện 1 IP thử > 10 username khác nhau trong 10 phút → chặn ngay.
- Chặn luỹ tiến: lần 1 = 30 phút, lần 2 = 2 giờ, lần 3 = 24 giờ, lần 4 = vĩnh viễn (chờ admin gỡ).
- Request từ IP bị chặn trả `429 Too Many Requests` (hoặc `403`), **không** chạm DB user/BCrypt để tránh tốn CPU.
- Có **whitelist IP** (mạng nội bộ cửa hàng) không bao giờ bị chặn.
- Chặn ngay từ **Filter** đặt trước Spring Security filter chain, dùng cache trong bộ nhớ (Caffeine, TTL ngắn) để không query DB mỗi request.

### 3.4. Lấy đúng IP client
- App chạy sau Nginx/Load Balancer: đọc `X-Forwarded-For` **chỉ khi** request đến từ proxy tin cậy (danh sách `trusted-proxies` cấu hình được). Nếu không, dễ bị giả mạo IP để né chặn. Viết `ClientIpResolver` xử lý đúng điều này, kèm unit test.

### 3.5. Chống dò tên tài khoản (User enumeration) & Timing attack
- Sai username hay sai mật khẩu đều trả **cùng một thông báo và cùng mã lỗi**: "Tên đăng nhập hoặc mật khẩu không đúng".
- Khi username không tồn tại vẫn thực hiện một phép so sánh BCrypt giả (dummy hash) để thời gian phản hồi tương đương.
- **KHÔNG dùng `Thread.sleep()` hay bất kỳ cách chặn thread nào để "cân bằng thời gian phản hồi"**: nó làm cạn thread pool khi bị spam và tự tạo lỗ hổng DoS. Dummy hash kết hợp rate limit theo IP là đủ.
- `forgot-password` luôn trả `200 OK` với thông báo chung dù email có tồn tại hay không.

### 3.6. Quên mật khẩu
- Token ngẫu nhiên 32 bytes bằng `SecureRandom`, gửi qua email dạng link. **Chỉ lưu SHA-256 hash của token trong DB**, không lưu token gốc.
- Hết hạn sau **15 phút**, **dùng 1 lần**. Yêu cầu token mới thì token cũ chưa dùng bị vô hiệu.
- Giới hạn: tối đa 3 yêu cầu/giờ/email và 10 yêu cầu/giờ/IP.
- Đặt lại mật khẩu thành công → thu hồi **toàn bộ refresh token** của user, mở khoá tài khoản nếu đang bị khoá, gửi email thông báo "mật khẩu đã được đổi".
- Mật khẩu mới không được trùng 3 mật khẩu gần nhất (bảng `password_history`).
- Chính sách mật khẩu: tối thiểu 8 ký tự, có chữ hoa, chữ thường, số; không chứa username; không nằm trong danh sách mật khẩu phổ biến (cho một danh sách nhỏ mẫu).

### 3.3b. Tài khoản bị vô hiệu / nhân viên nghỉ việc
- `enabled = false` hoặc user bị xoá mềm: mọi access token đang còn hạn phải bị từ chối nhanh. Đề xuất và cài đặt cơ chế (ví dụ trường `token_version` trong user, nhúng vào JWT, so sánh khi xác thực; hoặc blocklist `jti`). Giải thích lựa chọn.

### 3.7. Token
- Access token JWT sống **15 phút**, ký HS256 hoặc RS256 (nêu ưu nhược điểm, mặc định HS256 với secret lấy từ biến môi trường, **không hardcode**).
- Refresh token là chuỗi ngẫu nhiên, **lưu hash trong DB**, sống 7 ngày, gắn với `device_info`, `ip`, `user_agent`.
- **Rotation**: mỗi lần refresh sinh refresh token mới, token cũ bị đánh dấu đã dùng.
- **Reuse detection**: nếu một refresh token đã dùng bị gửi lại → coi là bị đánh cắp, thu hồi **cả họ token (token family)** của phiên đó và ghi audit log mức `HIGH`.
- Xử lý race condition khi client gửi 2 request refresh cùng lúc (cho phép "grace period" vài giây hoặc dùng khoá hàng `SELECT ... FOR UPDATE`; chọn một và giải thích).

### 3.8. Các ca biên khác
- Tài khoản chưa kích hoạt / bị khoá vĩnh viễn: thông báo phù hợp nhưng không lộ thông tin nhạy cảm.
- Đồng hồ server lệch/múi giờ: lưu UTC, hiển thị theo `Asia/Ho_Chi_Minh`.
- Request body quá lớn, thiếu field, sai định dạng: validation chuẩn (`@Valid`), lỗi trả theo format thống nhất.
- Đăng nhập thành công từ **IP/thiết bị mới**: gửi email thông báo.
- Xoá dữ liệu cũ: job định kỳ (`@Scheduled`) dọn token hết hạn, login_attempts > 90 ngày, chặn IP đã hết hạn. Chạy an toàn khi có nhiều instance (dùng ShedLock hoặc giải thích cách tránh chạy trùng).

## 4. THIẾT KẾ DATABASE (MySQL)

Viết script Flyway `V1__auth_schema.sql` cho các bảng sau, kèm **khoá chính, khoá ngoại, index, unique, kiểu dữ liệu hợp lý, comment**:

| Bảng | Mục đích chính |
|---|---|
| `users` | id, username (unique), email (unique), password_hash, full_name, status (ACTIVE/LOCKED/DISABLED), failed_login_count, lock_until, lock_level, token_version, last_login_at, last_login_ip, created_at, updated_at, `version` (optimistic lock) |
| `roles`, `user_roles` | RBAC, seed 4 role |
| `refresh_tokens` | id, user_id, token_hash (unique), family_id, status (ACTIVE/USED/REVOKED), expires_at, device_info, ip_address, user_agent, created_at, used_at |
| `login_attempts` | id, username_attempted, ip_address, success, failure_reason, user_agent, created_at (index theo ip+created_at và username+created_at) |
| `ip_blocklist` | id, ip_address, reason, block_level, blocked_until (NULL = vĩnh viễn), created_at, created_by |
| `ip_whitelist` | id, ip_or_cidr, note |
| `password_reset_tokens` | id, user_id, token_hash, expires_at, used_at, requested_ip, created_at |
| `password_history` | id, user_id, password_hash, created_at |
| `audit_logs` | id, user_id (nullable), event_type, severity, ip_address, user_agent, detail (JSON), created_at |

Yêu cầu: nêu lý do chọn từng index; cân nhắc partition hoặc chính sách dọn dẹp cho `login_attempts` và `audit_logs` vì bảng tăng rất nhanh.

## 5. API (REST, prefix `/api/v1/auth`)

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/login` | Đăng nhập |
| POST | `/refresh` | Làm mới token |
| POST | `/logout` | Thu hồi refresh token hiện tại |
| POST | `/logout-all` | Thu hồi mọi phiên |
| POST | `/forgot-password` | Yêu cầu đặt lại |
| POST | `/reset-password` | Đặt lại bằng token |
| POST | `/change-password` | Đổi mật khẩu |
| GET | `/sessions` | Danh sách phiên đang đăng nhập |
| DELETE | `/sessions/{id}` | Thu hồi 1 phiên |
| POST | `/admin/users/{id}/unlock` | Mở khoá (OWNER/MANAGER) |
| DELETE | `/admin/ip-blocks/{ip}` | Bỏ chặn IP |
| GET | `/admin/login-attempts` | Xem log (phân trang, lọc) |

Với mỗi endpoint hãy cho: request/response JSON mẫu, các mã lỗi (400, 401, 403, 423, 429...), và header liên quan.

**Format lỗi thống nhất:**
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
Dùng `@RestControllerAdvice` và enum mã lỗi. Thông báo cho người dùng bằng tiếng Việt.

## 6. BẢO MẬT BỔ SUNG

- CORS cấu hình theo whitelist domain; CSRF tắt hợp lý vì dùng token header (giải thích)
- Security headers (HSTS, X-Content-Type-Options, X-Frame-Options)
- Không log mật khẩu, token, hay hash ở bất kỳ đâu. Che (mask) dữ liệu nhạy cảm trong log
- Mọi secret (JWT secret, DB password, SMTP) đọc từ biến môi trường/`application-*.yml`, không commit
- Tuỳ chọn CAPTCHA: sau 3 lần sai thì response báo `captchaRequired=true` (chỉ cần thiết kế interface `CaptchaVerifier`, chưa cần tích hợp thật)
- Rate limit chung cho endpoint `/login` theo IP (ví dụ 10 request/phút/IP) bằng thuật toán sliding window hoặc token bucket

## 7. CẤU HÌNH (`application.yml`)

Gom toàn bộ ngưỡng vào `@ConfigurationProperties(prefix = "app.security")` để chỉnh không cần sửa code, ví dụ:

```yaml
app:
  security:
    login:
      max-failed-attempts: 5
      lock-durations-minutes: [15, 30, 60, 1440]
      max-sessions-per-user: 5
    ip-block:
      window-minutes: 10
      max-failures-per-ip: 20
      max-distinct-usernames-per-ip: 10
      block-durations-minutes: [30, 120, 1440]
    jwt:
      access-ttl-minutes: 15
      refresh-ttl-days: 7
    password-reset:
      ttl-minutes: 15
      max-per-hour-per-email: 3
    trusted-proxies: ["10.0.0.0/8"]
```

## 8. LOGGING & AUDIT

Ghi audit cho: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`, `IP_BLOCKED`, `IP_UNBLOCKED`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`, `PASSWORD_CHANGED`, `REFRESH_TOKEN_REUSE_DETECTED`, `SESSION_REVOKED`, `LOGOUT_ALL`. Ghi audit **bất đồng bộ**, không để lỗi ghi log làm hỏng luồng đăng nhập.

## 9. TESTING (bắt buộc)

- **Unit test** (Mockito): `LoginService` (đúng/sai mật khẩu, khoá tài khoản, hết thời gian khoá), `PasswordResetService`, `RefreshTokenService` (rotation + reuse), `ClientIpResolver`, `IpBlockService`
- **Integration test** (Testcontainers MySQL + MockMvc): luồng login → refresh → logout; 5 lần sai → bị khoá → mở khoá; 1 IP spam nhiều username → bị chặn 429; quên mật khẩu đầy đủ; token reset dùng lại lần 2 phải lỗi; reuse refresh token → thu hồi cả họ
- **Concurrency test**: 50 thread cùng đăng nhập sai 1 tài khoản → bộ đếm phải đúng, không mất cập nhật; 2 request refresh đồng thời với cùng 1 token
- Mục tiêu coverage ≥ 80% cho package `service`

## 10. YÊU CẦU CHẤT LƯỢNG CODE

- Tuân thủ SOLID, đặt tên rõ nghĩa, method ngắn, không magic number (dùng config/constant)
- Dùng constructor injection, `@Transactional` đặt đúng chỗ (nêu rõ chỗ nào `readOnly`, chỗ nào `REQUIRES_NEW` cho việc ghi login_attempts để không bị rollback khi login thất bại)
- Có Javadoc cho class/public method quan trọng; comment giải thích **lý do** ở các đoạn bảo mật nhạy cảm
- **Bắt buộc viết trọn vẹn toàn bộ mã nguồn của từng file.** TUYỆT ĐỐI KHÔNG dùng comment kiểu `// ... existing code ...`, `// tương tự như trên`, hay bỏ qua getter/setter/constructor (nếu không dùng Lombok). Nếu một file quá dài, hãy dừng và báo tôi để chia nhỏ, đừng cắt xén
- Không dùng API deprecated của Spring Security 6 (dùng `SecurityFilterChain`, không dùng `WebSecurityConfigurerAdapter`)
- Xử lý exception cụ thể, không `catch (Exception e)` rỗng

## 11. ĐẦU RA MONG MUỐN

1. Sơ đồ luồng (mermaid hoặc ASCII) cho: login, khoá tài khoản + chặn IP, refresh rotation, quên mật khẩu
2. Cấu trúc thư mục dự án
3. `pom.xml` (dependency cần thiết)
4. Flyway SQL
5. Toàn bộ code: entity, repository, service, filter, config, controller, exception, DTO
6. Test
7. File `README` ngắn: cách chạy, biến môi trường, ví dụ `curl` cho từng API
8. Mục **"Rủi ro còn lại & hướng nâng cấp"** (2FA/TOTP, đăng nhập bằng Google, device fingerprint, WAF...)

## 12. CÁCH LÀM VIỆC (QUAN TRỌNG)

Đừng viết toàn bộ trong một lần. Công việc chia thành các giai đoạn bên dưới. **Tôi sẽ chỉ định giai đoạn cần làm** (ví dụ: "làm giai đoạn 3"). Quy tắc:

- Chỉ làm **đúng giai đoạn tôi yêu cầu**, không làm trước các giai đoạn sau.
- Nếu giai đoạn đó phụ thuộc phần chưa có, hãy nêu rõ phần còn thiếu và giả định bạn dùng (không tự viết lan sang giai đoạn khác).
- Cuối mỗi giai đoạn: tóm tắt các file đã tạo/sửa, cách chạy/kiểm tra, rồi **dừng lại chờ tôi chỉ định giai đoạn tiếp theo**.
- Nếu tôi không nói rõ giai đoạn nào, hãy hỏi lại.

- **Giai đoạn 1:** Kiến trúc, sơ đồ luồng, thiết kế DB (SQL), danh sách API, các quyết định thiết kế quan trọng cần tôi duyệt
- **Giai đoạn 2:** Entity, repository, config, JWT, Security config, cấu trúc filter
- **Giai đoạn 3:** Login + khoá tài khoản + chặn IP + rate limit
- **Giai đoạn 4:** Refresh token (rotation/reuse), logout, quản lý phiên
- **Giai đoạn 5:** Quên mật khẩu, đổi mật khẩu, email, audit, job dọn dẹp
- **Giai đoạn 6A:** Unit test cho phần cốt lõi (`LoginService`, `RefreshTokenService`, `PasswordResetService`, `ClientIpResolver`, `IpBlockService`)
- **Giai đoạn 6B:** Integration test (Testcontainers), concurrency test (nhiều thread) và README

Nếu có điểm chưa rõ hoặc có nhiều cách làm, hãy **nêu phương án, ưu/nhược và đề xuất 1 phương án** thay vì tự đoán âm thầm. Nếu yêu cầu nào của tôi chưa an toàn hoặc chưa hợp lý, hãy phản biện thẳng.
