# Kết quả kiểm tra bản đầu ngày 24 tháng 10 năm 2026

- `mvn verify`: 17 test qua, 0 lỗi. Test SQL Server riêng được bỏ qua ở máy phát triển vì không có SQL Server/Docker.
- `npm ci` và `npm run build`: thành công.
- Chạy Spring Boot demo và gọi HTTP thật: đăng nhập admin, tải danh mục, cấp voucher 15%, tạo đơn 2 vé và 1 dịch vụ, tính giảm tổng đơn, thanh toán thử, sinh 2 QR PNG, check-in một vé và từ chối lần thứ hai: thành công.
- Kiểm tra YAML Compose và GitHub Actions: parse thành công. Docker images/Compose chưa được chạy tại máy phát triển.
- Có job GitHub Actions SQL Server thực: tạo database, Flyway validate, tạo đơn/voucher, thu tiền mặt và check-in. Xem kết quả Actions của commit; không coi cấu hình CI là kết quả kiểm thử.
- Trình duyệt cloud không truy cập được máy chủ phát triển do ERR_BLOCKED_BY_CLIENT. Chưa xác nhận trực quan React bằng trình duyệt. Cần thực hiện UAT theo README, đặc biệt mobile/in vé.

Đây là bản MVP để thử nghiệp vụ và tiếp tục phát triển. Thanh toán online thực, OTP và các tích hợp gửi vé chưa được triển khai.

## Nâng Java 25 và Spring Boot 4

- Spring Boot 4.0.9; starter Web MVC, Flyway và test tương ứng Boot 4.
- Build lại bằng JDK Temurin 25.0.5: `mvn clean verify` thành công, 17 test qua; test SQL Server chuyển sang CI do máy phát triển không có SQL Server/Docker.
- CI dùng Java 25 cho cả backend và test SQL Server, thêm build image backend Java 25. Xem Actions của commit nâng cấp để xác nhận các kiểm tra trên runner.

## Lô vé nội bộ và PDF

- JDK 25 / Spring Boot 4: 29 test local qua, gồm 28 test nghiệp vụ/tích hợp và 1 test chặn thanh toán giả lập khi tắt cấu hình. SQL Server được chạy riêng trên CI.
- Kiểm thử kho mã: thiếu mã, giữ mã đồng thời, hủy/giải phóng, đơn cũ không giải phóng mã đã được đơn mới giữ, chuyển SOLD/USED, hạn lô và tạm dừng lô.
- Kiểm thử migration từ V1 có nhiều vé cũ sang V2, giữ nguyên dữ liệu legacy; H2 dùng UNIQUE NULLS DISTINCT, SQL Server dùng filtered unique index để cho phép nhiều vé legacy chưa có stock_id.
- Kiểm thử PDF 2 trang: đọc lại tiếng Việt, render từng trang và giải mã QR từ ảnh, đối chiếu đúng tập token của hai vé đã thanh toán.
- Render PDF ra PNG và kiểm tra cả hai trang: bố cục A6, tiếng Việt, mã vé, lô, QR và chân trang rõ ràng.
- HTTP thực: cấp voucher, đặt vé + dịch vụ, thanh toán thử, lấy PDF thật, kiểm tra tồn mã và check-in/chặn dùng lại: thành công.
- Build frontend thành công. Trình duyệt cloud chưa truy cập được máy chủ phát triển, cần UAT màn hình tại máy khách theo README.
