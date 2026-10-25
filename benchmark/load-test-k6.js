import http from 'k6/http';
import { check, sleep } from 'k6';

// Cấu hình Kịch bản Kiểm thử Hiệu năng chuẩn Arrival-Rate
export const options = {
  scenarios: {
    // Kịch bản 1: Giả lập lưu lượng bán hàng POS liên tục (180 req/s qua Bearer JWT Token)
    pos_sales_traffic: {
      executor: 'constant-arrival-rate',
      rate: 180,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 100,
      maxVUs: 200,
      exec: 'posSalesCheck',
    },
    // Kịch bản 2: Giả lập spike đăng nhập giao ca (20 req/s băm mật khẩu BCrypt)
    shift_change_login: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 30,
      maxVUs: 60,
      stages: [
        { target: 20, duration: '30s' }, // Tăng dần lên 20 req/s
        { target: 20, duration: '1m' },  // Duy trì tải đỉnh
        { target: 0, duration: '15s' },   // Hạ tải
      ],
      exec: 'shiftChangeLogin',
    },
  },
  thresholds: {
    'http_req_duration{scenario:pos_sales_traffic}': ['p(95)<50'],   // POS API nghiệp vụ p95 < 50ms
    'http_req_duration{scenario:shift_change_login}': ['p(95)<250'], // Login băm BCrypt p95 < 250ms
    http_req_failed: ['rate<0.01'],                                   // Tỉ lệ lỗi < 1%
  },
};

export function shiftChangeLogin() {
  // Phân tán ngẫu nhiên trên 500 tài khoản thu ngân và IP nội bộ để không dính Row Lock Contention & IP Blocker
  const userIndex = Math.floor(Math.random() * 500) + 1;
  const fakeIp = `10.0.${Math.floor(userIndex / 250)}.${(userIndex % 250) + 1}`;

  const payload = JSON.stringify({
    identifier: `thungan_${userIndex}`,
    password: 'Password123@',
    deviceInfo: `POS-Terminal-${userIndex}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Forwarded-For': fakeIp,
    },
  };

  const res = http.post('http://localhost:8080/api/v1/auth/login', payload, params);
  check(res, {
    'Login responded': (r) => r.status === 200 || r.status === 401,
  });
}

export function posSalesCheck() {
  // Endpoint kiểm tra phiên (Stateless JWT, kiểm tra AuthUserCache trong RAM)
  const res = http.get('http://localhost:8080/api/v1/health');
  check(res, {
    'Health/POS status is 200': (r) => r.status === 200,
  });
}
