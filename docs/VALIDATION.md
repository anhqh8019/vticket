# Kiểm tra phiên bản VietQR

- Local: 59 test thành công, 2 test SQL Server bỏ qua khi thiếu SQLSERVER_TEST_URL. Bao gồm kiểm thử lõi đặt vé/voucher/kho/check-in/PDF, outgoing API VietQR bằng mock HTTP, callback và JWT.
- Kiểm tra VietQR: chỉ tạo QR chưa cấp vé; callback đúng mới PAID và tải PDF; token callback tách tài khoản nhân viên; CSRF giữ nguyên cho ứng dụng; callback sai số tiền/tài khoản/nội dung, ghi nợ, đơn lạ, đến muộn và voucher hết hạn vào REVIEW; gửi lặp và callback đồng thời chỉ cấp vé một lần.
- Kiểm tra quyền: online không được thu tiền mặt, tại quầy được; chọn VietQR không chuyển sang tiền mặt trên cùng đơn; không còn demo-pay. Đơn 0 đồng không gọi ngân hàng.
- PNG thanh toán giải mã trùng payload provider trả về. PDF vé tiếp tục được kiểm tra tiếng Việt, số trang, render và giải mã QR từng vé.
- Frontend: Vite production build thành công. Không thực hiện giao dịch thật với sandbox/production VietQR khi chưa có credentials và public callback của khách hàng.
- CI kiểm tra riêng Java 25 + Spring Boot 4, build container, frontend và SQL Server (nâng từ schema V1, giữ vé cũ, bán tiền mặt và callback VietQR chống trùng). Kết quả thực tế xem GitHub Actions của commit.

Các kiểm thử provider dùng test double, không phải bằng chứng tài khoản ngân hàng đã được VietQR kích hoạt. Cần kiểm tra nghiệm thu theo VIETQR_SETUP.md trước khi mở bán.
