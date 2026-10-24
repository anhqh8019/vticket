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

PENDING -> PAID hoặc CANCELLED/EXPIRED. Đơn 15 phút, job quét mỗi phút. Dù job chưa chạy, thanh toán vẫn kiểm tra expiresAt; không tự xác nhận thanh toán cho đơn đã quá hạn.

Voucher giảm trên tổng vé + dịch vụ, VND làm tròn HALF_UP đến đồng, 1–100%. Một mã/đơn. Khi tạo đơn, khóa ghi voucher để chỉ một đơn giữ được mã. Hết thời gian giữ thì đơn mới có thể giữ lại. Khi thanh toán, khóa đơn rồi voucher, kiểm tra thời hạn và chủ đơn giữ, đánh dấu đã dùng cùng transaction với phát hành vé. Gọi xác nhận lại đơn đã PAID không phát hành trùng. Hủy đơn pending giải phóng voucher nếu đơn đó vẫn sở hữu lượt giữ. Thu hồi voucher đang giữ hoặc đã dùng bị từ chối.

Check-in khóa ghi ticket trước khi kiểm tra và ghi lượt. Chỉ vé PAID đúng ngày Việt Nam mới được nhận. Hai thiết bị đồng thời chỉ một thiết bị thành công.

## Truy cập

Admin và staff là hai tài khoản bootstrap từ biến môi trường, mật khẩu BCrypt, role ADMIN/STAFF. HTTP Basic gửi từ bộ nhớ frontend, không lưu mật khẩu vào localStorage. Bắt buộc HTTPS trước khi triển khai Internet. API thay đổi dữ liệu có CSRF; frontend lấy token từ `/api/csrf`.

Khách chưa cần tài khoản: truy cập đơn bằng accessToken UUID ngẫu nhiên, không tra cứu bằng số điện thoại công khai. Chỉ lưu mã đơn gần nhất trong localStorage, không lưu hồ sơ/mật khẩu. Không chia sẻ mã truy cập đơn.

Voucher cần cả mã ngẫu nhiên và số điện thoại khớp. **Chưa xác minh quyền sở hữu số điện thoại bằng OTP**; người biết cả mã lẫn số điện thoại có thể dùng. Nếu cần khóa theo danh tính đã xác thực, bổ sung OTP trước vận hành công khai.

## Thanh toán

ONLINE chỉ VIETQR; COUNTER có CASH (STAFF/ADMIN) hoặc VIETQR. Đơn 0 đồng có xác nhận riêng, không phát sinh giao dịch tiền. Profile demo chỉ thêm H2/dữ liệu mẫu, không có thanh toán giả lập.

`payment_intents` lưu một giao dịch/đơn, mã provider 13 ký tự, snapshot số tiền/tài khoản, payload QR trả từ API và trạng thái CREATING/READY/FAILED/PAID/CLOSED. Giữ khóa đơn trong transaction ngắn trước/sau HTTP, không giữ khóa database trong khi gọi VietQR. Retry dùng lại mã provider; QR READY không tạo lại. Chọn VietQR khóa lựa chọn tiền mặt; muốn đổi phải hủy đơn pending và tạo lại để tránh nhận tiền hai lần.

Hai chiều xác thực độc lập: vticket gọi token_generate + generate-customer bằng thông tin API VietQR cung cấp; VietQR gọi endpoint token của vticket bằng Basic callback credentials rồi dùng JWT HS256 thời hạn mặc định 300 giây để gọi transaction-sync. Security chain callback tách khỏi HTTP Basic nhân viên và CSRF của ứng dụng. Không tin redirect, ảnh QR hay xác nhận từ trình duyệt.

Callback kiểm tra mã đơn, tài khoản/VA, credit C, số tiền, nội dung và thời gian; transaction ID và tài khoản + tham chiếu ngân hàng chống trùng. `payment_callback_lock` serialize ghi nhận callback trên nhiều instance; `payment_receipts` lưu APPLIED hoặc REVIEW, cùng transaction với cấp vé/voucher. Sai, đến muộn, đơn đã hủy/đã trả tiền hoặc hết kho/voucher được lưu REVIEW và trả ACK, không tự phát vé. Admin xem 200 giao dịch gần nhất để đối soát ngoài hệ thống, chưa có hoàn tiền tự động. Xem VIETQR_SETUP.md.

## Ngoài phạm vi v0.1

Chưa có OTP, email/SMS/Zalo, khách đoàn, tích điểm, hóa đơn điện tử, đổi/hủy sau thanh toán, hoàn tiền, quét camera, quản lý nhân viên CRUD, báo cáo phân trang dài hạn, kiểm soát riêng từng dịch vụ. Chưa tính trẻ em và hạn mức ngày/giờ theo thỏa thuận.

## Bổ sung kho mã và PDF

`ticket_batches` và `ticket_stock` lưu lô, mã gốc, QR token, trạng thái và đơn đang giữ/đã bán. `tickets` bổ sung stockId, ticketCode, batchCode; vé cũ giữ giá trị null. BookingService khóa các sản phẩm theo ID tăng dần trong create/pay/cancel; InventoryService cấp mã nguyên tử. Khi thanh toán, chuyển mã giữ sang SOLD và tạo Ticket theo QR đã có, không sinh mã mới. Check-in cập nhật Ticket và StockCode sang USED trong cùng transaction.

PDFBox xuất A6 theo từng vé, nhúng font DejaVu Sans đã subset Latin/tiếng Việt (license kèm resource). Kiểm thử render PDF và giải mã QR từ ảnh để đối chiếu với token đã cấp. Chi tiết xem TICKET_BATCHES.md. Tích hợp Smile chưa được triển khai.
