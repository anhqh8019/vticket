# Gửi email vé sau thanh toán

## Hành vi

- Khách có thể để trống email. Khi bật tính năng, đơn có email được xếp hàng gửi trong cùng transaction chuyển sang PAID và phát hành vé.
- Áp dụng cho online VietQR (callback hợp lệ), quầy tiền mặt/VietQR và xác nhận đơn tổng 0 đồng. Pending, hủy, hết hạn và giao dịch REVIEW không gửi email.
- Email tiếng Việt gồm mã đơn, ngày dùng, số vé, số tiền đã thanh toán và đường dẫn tải PDF/ZIP. Đơn dưới 300 vé đính kèm PDF gộp. Đơn từ 300 vé đính kèm ZIP, mỗi PDF một vé, kể cả đặt vé lẻ số lượng lớn.
- Nếu file vượt giới hạn mặc định 8.000.000 byte, gửi email có đường dẫn tải, không đính kèm. Giới hạn này tính trước mã hóa MIME; kích thước email thực lớn hơn khoảng 1/3. Chọn giới hạn thấp hơn mức tối đa nhà cung cấp SMTP.
- Email gửi nền sau khi transaction thanh toán commit, thường bắt đầu trong khoảng 5 giây khi hàng đợi rảnh. Lỗi SMTP không đảo thanh toán, không phát hành lại vé và không chặn khách tải trực tiếp.
- Mỗi đơn có một bản ghi hàng đợi. Callback lặp hoặc xác nhận thu tiền lặp không thêm job. Retry không sinh mã vé mới.
- Trạng thái: PENDING, SENDING, RETRY, SENT hoặc FAILED. SENT nghĩa là SMTP chấp nhận gửi, không đảm bảo đã vào Inbox; cần kiểm tra thư rác/bounce tại hộp thư gửi.
- Nếu SMTP đã chấp nhận nhưng backend mất kết nối/khởi động lại trước khi lưu SENT, sau khi lease hết hạn có thể gửi lại cùng vé. SMTP không hỗ trợ giao dịch exactly-once với database; không cam kết tuyệt đối một email trong tình huống này.

## Cấu hình

Tính năng mặc định tắt. Cần có SMTP hợp lệ của đơn vị vận hành. Không dùng credentials VietQR làm SMTP. Không gửi password trong chat/git.

| Biến | Nội dung |
|---|---|
| TICKET_EMAIL_ENABLED | true để bật trước khi nhận đơn mới |
| TICKET_EMAIL_FROM | Một địa chỉ gửi đã được SMTP cho phép, ví dụ tickets@example.com |
| PUBLIC_BASE_URL | Origin website khách truy cập, ví dụ https://tickets.example.com; không có path/query/tài khoản |
| SMTP_HOST | Máy chủ SMTP do nhà cung cấp cấp |
| SMTP_PORT | 587 mặc định cho STARTTLS; 465 nếu SMTP yêu cầu TLS trực tiếp |
| SMTP_USERNAME / SMTP_PASSWORD | Tài khoản/mật khẩu SMTP hoặc app password theo nhà cung cấp |
| SMTP_AUTH | true mặc định; false chỉ cho relay nội bộ đã kiểm soát |
| SMTP_STARTTLS | true mặc định cho 587; đặt false nếu dùng SSL trực tiếp 465 |
| SMTP_SSL | false mặc định; true cho SSL trực tiếp theo SMTP của đơn vị |
| TICKET_EMAIL_WORKER_ENABLED | true mặc định; false để tạm dừng gửi trong khi vẫn xếp hàng |
| TICKET_EMAIL_MAX_ATTEMPTS | 5 mặc định, nhận 1..10 |
| TICKET_EMAIL_ATTACHMENT_LIMIT_BYTES | 8000000 mặc định, nhận 1024..20000000 |

PUBLIC_BASE_URL là cấu hình cố định, không lấy từ Host của request. Dùng HTTPS công khai khi vận hành. Các đường dẫn tải có accessToken riêng tư của đơn; chỉ gửi đến email khách nhập, không CC/BCC. Không chia sẻ URL/log chứa token.

Timeout SMTP: kết nối 5 giây, đọc/ghi 15 giây. SMTP dùng TLS và kiểm tra chứng chỉ mặc định, không cấu hình trust-all.

Docker: đặt trong `.env`, sau đó `docker compose up -d --build backend frontend`. Chạy Java/Maven trực tiếp không tự đọc `.env`: đặt biến cho đúng tiến trình backend.

### WinSW trên Windows

Thêm vào XML service backend, thay giá trị ví dụ bằng thông tin thực rồi restart service:

```xml
<env name="TICKET_EMAIL_ENABLED" value="true" />
<env name="TICKET_EMAIL_FROM" value="tickets@example.com" />
<env name="PUBLIC_BASE_URL" value="https://tickets.example.com" />
<env name="SMTP_HOST" value="smtp.example.com" />
<env name="SMTP_PORT" value="587" />
<env name="SMTP_USERNAME" value="tickets@example.com" />
<env name="SMTP_PASSWORD" value="THAY_BANG_MAT_KHAU_SMTP" />
<env name="SMTP_AUTH" value="true" />
<env name="SMTP_STARTTLS" value="true" />
<env name="SMTP_SSL" value="false" />
```

Nếu mật khẩu chứa ký tự đặc biệt XML như `&`, phải encode thành `&amp;`. Dùng tài khoản gửi riêng và hạn chế quyền đọc file cấu hình. Nếu nhà cung cấp chỉ chấp nhận OAuth, cần cấu hình relay phù hợp hoặc bổ sung OAuth; không giả định mọi tài khoản email đều cho SMTP username/password.

## Vận hành và thử lại

- Migration V8 tạo `ticket_email_jobs` trong VTICKET, không sửa Smile. Nâng cấp backend trước khi mở lại frontend. Các đơn PAID trước nâng cấp hoặc trong lúc TICKET_EMAIL_ENABLED=false không được tự động gửi bù.
- Khi bật chức năng nhưng SMTP lỗi, job vẫn được lưu và thanh toán vẫn thành công. Worker thử lại sau 60, 120, 240... giây, tối đa 1 giờ giữa các lần; dừng ở max attempts. Lease 15 phút bảo vệ cùng job khỏi hai worker đồng thời.
- Khi restart, job PENDING/RETRY tiếp tục; job SENDING chỉ được nhận lại khi lease hết hạn. Dùng TICKET_EMAIL_WORKER_ENABLED=false để dừng gửi có kiểm soát.
- Trang đơn PAID có email hiển thị trạng thái chờ gửi, SMTP đã chấp nhận hoặc gửi lỗi; khách vẫn tải được PDF/ZIP trực tiếp.
- ADMIN có API `GET /api/admin/ticket-emails` xem 200 job gần nhất (có địa chỉ nhận), và `POST /api/admin/ticket-emails/{bookingId}/retry` thử lại job RETRY/FAILED. POST cần ADMIN và CSRF như các thao tác quản trị khác; retry reset số lần thử. SENT/SENDING/PENDING không cho bấm gửi lặp. Chưa có màn hình quản trị email riêng.
- `GET /api/public/orders/{accessToken}` thêm `emailDelivery: {state, attempts, sentAt}` hoặc null nếu không có job. Không trả lỗi SMTP/bí mật qua trạng thái công khai.
- last_error chỉ lưu mã tổng quát, không lưu nguyên exception SMTP vì có thể chứa thông tin nhạy cảm. Kiểm tra host/port/TLS/auth/quyền FROM và dashboard nhà cung cấp để xác định lỗi. Không đổi email khách sau thanh toán qua API công khai.

## Nghiệm thu

1. Trên staging dùng hộp thư thử do đơn vị vận hành kiểm soát; cấu hình FROM và PUBLIC_BASE_URL đúng website.
2. Đặt đơn không có email rồi thanh toán: không có job/email, tải vé bình thường.
3. Đặt có email, thử riêng quầy cash và online VietQR; chỉ sau PAID mới có job và nhận email đúng số vé/tiền/ngày. Mở PDF thực và đọc QR/barcode.
4. Đơn 0 đồng và callback gửi lại không tạo thêm job/vé. Đơn pending/cancel/REVIEW không được gửi vé.
5. Đơn 300/500: giải nén đúng số PDF riêng. Giảm giới hạn file trên staging để kiểm tra email không đính kèm vẫn có link tải đầy đủ.
6. Sai SMTP tạm thời: đơn vẫn PAID, job RETRY/FAILED; khôi phục SMTP và retry bằng ADMIN; không tăng số vé hay trừ thêm tồn.
7. Restart backend khi còn job và kiểm tra tiếp tục gửi. Kiểm tra bằng email thật trên các hộp thư mục tiêu và thư rác. Bật SPF/DKIM/DMARC theo hướng dẫn của nhà cung cấp/tên miền gửi để cải thiện chuyển phát.

Kiểm thử tự động dùng SMTP giả trong bộ nhớ để kiểm chứng MIME/PDF/ZIP, không gửi thư thật. Cần nghiệm thu SMTP thật sau khi đơn vị vận hành cấu hình tài khoản.
