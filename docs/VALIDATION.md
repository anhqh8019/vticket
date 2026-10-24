# Kết quả kiểm tra bản đầu ngày 24 tháng 10 năm 2026

- `mvn verify`: 17 test qua, 0 lỗi. Test SQL Server riêng được bỏ qua ở máy phát triển vì không có SQL Server/Docker.
- `npm ci` và `npm run build`: thành công.
- Chạy Spring Boot demo và gọi HTTP thật: đăng nhập admin, tải danh mục, cấp voucher 15%, tạo đơn 2 vé và 1 dịch vụ, tính giảm tổng đơn, thanh toán thử, sinh 2 QR PNG, check-in một vé và từ chối lần thứ hai: thành công.
- Kiểm tra YAML Compose và GitHub Actions: parse thành công. Docker images/Compose chưa được chạy tại máy phát triển.
- Có job GitHub Actions SQL Server thực: tạo database, Flyway validate, tạo đơn/voucher, thu tiền mặt và check-in. Xem kết quả Actions của commit; không coi cấu hình CI là kết quả kiểm thử.
- Trình duyệt cloud không truy cập được máy chủ phát triển do ERR_BLOCKED_BY_CLIENT. Chưa xác nhận trực quan React bằng trình duyệt. Cần thực hiện UAT theo README, đặc biệt mobile/in vé.

Đây là bản MVP để thử nghiệp vụ và tiếp tục phát triển. Thanh toán online thực, OTP và các tích hợp gửi vé chưa được triển khai.
