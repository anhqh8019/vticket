# Kiến trúc vticket

React/Vite -> API Spring Boot 4.0.9 (Java 25) -> SQL Server. Frontend và API cùng origin qua Nginx; Vite proxy dùng khi phát triển. Không triển khai nghiệp vụ đơn/voucher ở trình duyệt.

## Dữ liệu

- `products`: TICKET, SERVICE hoặc COMBO; ngừng bán bằng active=false, giữ lịch sử.
- `product_services`: dịch vụ bao gồm trong giá vé/combo; mua thêm là dòng riêng trong đơn.
- `vouchers`: mã ngẫu nhiên cấp riêng, khách nhận, số điện thoại, %, hiệu lực [validFrom, validUntil), trạng thái thu hồi, giữ chỗ, đã dùng.
- `bookings`/`booking_lines`: đơn và snapshot giá, tên, dịch vụ bao gồm tại thời điểm đặt. Hai kênh ONLINE/COUNTER.
- `tickets`: một QR ngẫu nhiên cho mỗi vé/combo trong đơn; không phát QR riêng cho dòng dịch vụ mua thêm. Nhân viên xem dịch vụ trên đơn; chưa có kiểm soát từng lượt dịch vụ.
- `audit_events`: nhật ký tạo đơn, thanh toán, check-in, quản trị danh mục và voucher.

## Trạng thái và giao dịch

PENDING -> PAID hoặc CANCELLED/EXPIRED. Đơn 15 phút, job quét mỗi phút. Dù job chưa chạy, thanh toán vẫn kiểm tra expiresAt; không nhận tiền cho đơn đã quá hạn.

Voucher giảm trên tổng vé + dịch vụ, VND làm tròn HALF_UP đến đồng, 1–100%. Một mã/đơn. Khi tạo đơn, khóa ghi voucher để chỉ một đơn giữ được mã. Hết thời gian giữ thì đơn mới có thể giữ lại. Khi thanh toán, khóa đơn rồi voucher, kiểm tra thời hạn và chủ đơn giữ, đánh dấu đã dùng cùng transaction với phát hành vé. Gọi xác nhận lại đơn đã PAID không phát hành trùng. Hủy đơn pending giải phóng voucher nếu đơn đó vẫn sở hữu lượt giữ. Thu hồi voucher đang giữ hoặc đã dùng bị từ chối.

Check-in khóa ghi ticket trước khi kiểm tra và ghi lượt. Chỉ vé PAID đúng ngày Việt Nam mới được nhận. Hai thiết bị đồng thời chỉ một thiết bị thành công.

## Truy cập

Admin và staff là hai tài khoản bootstrap từ biến môi trường, mật khẩu BCrypt, role ADMIN/STAFF. HTTP Basic gửi từ bộ nhớ frontend, không lưu mật khẩu vào localStorage. Bắt buộc HTTPS trước khi triển khai Internet. API thay đổi dữ liệu có CSRF; frontend lấy token từ `/api/csrf`.

Khách chưa cần tài khoản: truy cập đơn bằng accessToken UUID ngẫu nhiên, không tra cứu bằng số điện thoại công khai. Chỉ lưu mã đơn gần nhất trong localStorage, không lưu hồ sơ/mật khẩu. Không chia sẻ mã truy cập đơn.

Voucher cần cả mã ngẫu nhiên và số điện thoại khớp. **Chưa xác minh quyền sở hữu số điện thoại bằng OTP**; người biết cả mã lẫn số điện thoại có thể dùng. Nếu cần khóa theo danh tính đã xác thực, bổ sung OTP trước vận hành công khai.

## Thanh toán

Profile mặc định SQL Server: chỉ có xác nhận CASH cho đơn COUNTER, quyền STAFF/ADMIN. Chưa kết nối cổng thanh toán, callback và hoàn tiền.

Profile `demo`: H2 file, dữ liệu mẫu và endpoint `demo-pay` để thử end-to-end online. Cấu hình `app.demo-payment` mặc định false; không bật ở production. Endpoint giả lập chỉ chạy khi bật rõ ràng. Đơn DEMO có paymentMethod=DEMO, không được coi là doanh thu tiền thật khi chuyển sang khai thác.

Tích hợp cổng thật tiếp theo: tạo giao dịch phía server, xác minh chữ ký callback, tra mã giao dịch với nhà cung cấp và kiểm tra số tiền/tiền tệ/order trước khi gọi BookingService.pay. Không xác nhận thanh toán từ redirect hoặc trình duyệt. Xử lý callback đến muộn bằng đối soát/hoàn tiền, không tự phát vé cho đơn hết hạn.

## Ngoài phạm vi v0.1

Chưa có OTP, email/SMS/Zalo, khách đoàn, tích điểm, hóa đơn điện tử, đổi/hủy sau thanh toán, hoàn tiền, thanh toán QR thật, quét camera, quản lý nhân viên CRUD, báo cáo phân trang dài hạn, kiểm soát riêng từng dịch vụ. Chưa tính trẻ em và hạn mức ngày/giờ theo thỏa thuận.
