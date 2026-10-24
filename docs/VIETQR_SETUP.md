# Cấu hình thanh toán VietQR

## Luồng đã triển khai

- Khách online chọn VietQR. Nhân viên tạo đơn COUNTER có thể thu tiền mặt hoặc chọn VietQR.
- Backend gọi API lấy token, sau đó gọi `POST /vqr/api/qr/generate-customer` với QR động (`qrType=0`, `transType=C`), số tiền từ đơn và `orderId` 13 ký tự. QR hiển thị từ đúng payload VietQR trả về, không tự ghép mã chuyển khoản.
- Chỉ callback báo có hợp lệ mới xác nhận thanh toán, dùng voucher và phát hành mã vé đã giữ. Trang khách cập nhật mỗi 3 giây; khi PAID hiển thị tải PDF, mỗi vé một QR. QR thanh toán và QR vào cổng là hai nội dung khác nhau.
- Tiền mặt yêu cầu STAFF/ADMIN xác nhận đã thu cho đơn COUNTER. Không có nút khách tự báo đã thanh toán, không còn demo-pay. Đơn tổng 0 đồng xác nhận riêng, không gọi ngân hàng.
- Smile PSM chưa tích hợp; QR vé hiện được kiểm soát bằng vticket.

## Hai bộ thông tin xác thực

| Chiều gọi         | Thông tin                    | Nguồn                                       |
| ----------------- | ---------------------------- | ------------------------------------------- |
| vticket → VietQR  | API username/password        | VietQR cấp cho đúng môi trường              |
| VietQR → vticket  | Callback username/password   | Đơn vị triển khai tạo và đăng ký với VietQR |
| Ký token callback | JWT secret tối thiểu 32 byte | Tạo ngẫu nhiên, chỉ lưu ở backend           |

Không dùng tài khoản admin/staff làm callback credentials. Không dùng lại tài khoản ngân hàng từ dự án khác. Không commit `.env`, credentials hoặc token; lưu bí mật trong môi trường triển khai.

## Biến môi trường

Sao chép `.env.example` thành `.env` khi chạy Docker Compose. Điền các biến:

| Biến                                     | Giá trị                                                               |
| ---------------------------------------- | --------------------------------------------------------------------- |
| VIETQR_ENABLED                           | true sau khi điền đủ cấu hình                                         |
| VIETQR_API_BASE_URL                      | Sandbox `https://dev.vietqr.org`; production `https://api.vietqr.org` |
| VIETQR_API_USERNAME                      | Username API do VietQR cấp                                            |
| VIETQR_API_PASSWORD                      | Password API do VietQR cấp                                            |
| VIETQR_BANK_CODE                         | Mã ngân hàng đã đăng ký, ví dụ MB (970422 được chuẩn hóa thành MB)    |
| VIETQR_BANK_ACCOUNT                      | Số tài khoản nhận đã liên kết VietQR                                  |
| VIETQR_BANK_ACCOUNT_NAME                 | Tên chủ tài khoản; backend chuẩn hóa sang không dấu                   |
| VIETQR_CALLBACK_USERNAME                 | Username riêng đăng ký cho VietQR gọi vào                             |
| VIETQR_CALLBACK_PASSWORD                 | Mật khẩu ngẫu nhiên tối thiểu 12 ký tự                                |
| VIETQR_CALLBACK_JWT_SECRET               | Chuỗi ngẫu nhiên tối thiểu 32 byte, giữ ổn định giữa các instance     |
| VIETQR_CALLBACK_TOKEN_EXPIRATION_SECONDS | 300 mặc định                                                          |

Bật VietQR nhưng thiếu callback secret hợp lệ sẽ chặn khởi động để tránh xác thực yếu. Thiếu API/tài khoản nhận sẽ không cho tạo QR. Khi tắt VietQR, vẫn bán tiền mặt tại quầy được. Sandbox không xác nhận tiền thật; chuyển sang production chỉ sau khi nghiệm thu với VietQR.

Chạy Maven trực tiếp **không tự đọc `.env`**. Đặt các biến trong cùng cửa sổ PowerShell trước khi chạy backend, ví dụ:

```powershell
$env:VIETQR_ENABLED = "true"
$env:VIETQR_API_BASE_URL = "https://dev.vietqr.org"
$env:VIETQR_API_USERNAME = "<username-api>"
$env:VIETQR_API_PASSWORD = "<password-api>"
$env:VIETQR_BANK_CODE = "MB"
$env:VIETQR_BANK_ACCOUNT = "<tai-khoan-da-dang-ky>"
$env:VIETQR_BANK_ACCOUNT_NAME = "<ten-chu-tai-khoan>"
$env:VIETQR_CALLBACK_USERNAME = "<callback-username>"
# Sinh hai giá trị khác nhau; lưu vào secret manager và đăng ký password với VietQR.
$env:VIETQR_CALLBACK_PASSWORD = ([guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N"))
$env:VIETQR_CALLBACK_JWT_SECRET = ([guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N"))
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Không sinh lại secret mỗi lần restart production. Profile demo dùng H2 và tồn vé mẫu, nhưng vẫn gọi VietQR sandbox thật nếu đã cấu hình; không giả lập thu tiền.

## Đăng ký callback HTTPS công khai

Với domain thực tế của khách hàng, đăng ký:

- Lấy token: `POST https://<domain>/vietqr/api/token_generate`
- Báo giao dịch: `POST https://<domain>/vietqr/bank/api/transaction-sync`

VietQR gọi endpoint token bằng HTTP Basic callback credentials. Kết quả gồm `access_token`, `token_type: Bearer`, `expires_in: 300`; dùng token đó gọi transaction-sync. Không dùng token outbound API để gọi callback.

Có alias `/api/vietqr/token_generate`, `/api/vietqr/transaction-sync`, `/api-vietqr-callback/api-get-token`, `/api-vietqr-callback/api-transaction-sync` nếu cần tương thích cấu hình cũ. Chọn một cặp URL thống nhất khi đăng ký.

Nginx và Vite đã proxy `/vietqr/`, `/api-vietqr-callback/` và `/api/` về backend. Reverse proxy bên ngoài cũng phải chuyển tiếp POST, body và Authorization, không đưa callback vào trang React, không áp Basic Auth của nhân viên lên đường callback. Lỗi 405 thường là chưa proxy POST; 401 cần phân biệt API credentials phía VietQR với callback credentials phía vticket. Không ghi token/password vào log.

## Body callback

Các tên trường theo tài liệu, đặc biệt `bankaccount` viết thường:

```json
{
  "bankaccount": "<tai-khoan-hoac-VA-tren-QR>",
  "amount": 450000,
  "transType": "C",
  "content": "<noi-dung-chinh-xac-tren-QR>",
  "transactionid": "<ma-giao-dich-duy-nhat>",
  "transactiontime": 1792800000000,
  "referencenumber": "<tham-chieu-ngan-hang-duy-nhat>",
  "orderId": "<ma-VietQR-13-ky-tu>"
}
```

Đây là mẫu cấu trúc; thời gian phải là epoch millisecond thực tế, amount/content/orderId phải lấy từ QR đã tạo. `orderId` không phải UUID của booking. Token hết hạn cần lấy lại. Production chỉ VietQR được gọi callback; không xây nút gửi callback từ frontend.

Callback trả `error`, `errorReason`, `toastMessage`, `object.reftransactionid`. Xác thực thiếu/sai trả 401, body sai trả 400, tắt tích hợp trả 503. Giao dịch hợp lệ được ghi APPLIED; giao dịch thực nhưng không khớp nghiệp vụ được ghi REVIEW và ACK `error=false` để tránh gửi lại vô hạn. ACK không đồng nghĩa đơn đã PAID; xem trạng thái đơn và nội dung thông báo. Callback trùng ID hoặc cùng tài khoản + tham chiếu chỉ ghi một lần; cùng mã nhưng dữ liệu xung đột trả 400.

## Đối soát và nghiệm thu

Admin → Đối soát VietQR hiển thị tối đa 200 callback gần nhất. Sai số tiền/tài khoản/nội dung, giao dịch ghi nợ, mã đơn lạ, tiền đến sau hạn giữ 15 phút, đơn hủy/đã trả tiền, voucher/kho không hợp lệ sẽ không cấp vé. Nhân viên kiểm tra sao kê và liên hệ khách; chưa có tự động hoàn tiền hay ép xác nhận giao dịch REVIEW.

Sau khi đã tạo QR, không thu tiền mặt trên cùng đơn. Nếu cần đổi, hủy đơn pending và tạo đơn khác; khách chuyển vào QR đã hủy sẽ vào đối soát. QR được giữ lại khi lỗi để retry cùng orderId, không liên tục tạo mã mới.

Trước mở bán:

1. Dùng tài khoản sandbox đúng môi trường, domain HTTPS và URL callback đã đăng ký.
2. Tạo lô còn tồn, mua online có/không voucher, tạo VietQR; kiểm tra đúng tài khoản, số tiền, nội dung. Chưa callback thì chưa có vé/PDF.
3. Theo API sandbox test callback của VietQR, dùng đúng bankAccount/content/amount trên QR. Kiểm tra backend nhận callback, trang khách tự chuyển PAID, tải PDF và scan từng vé tại vticket.
4. Gửi lại callback: không phát hành thêm vé. Thử sai số tiền và hết hạn đơn: ghi REVIEW, không cấp vé.
5. Tại quầy kiểm tra riêng tiền mặt và VietQR. Chỉ STAFF/ADMIN được xác nhận tiền mặt, đơn ONLINE bị từ chối.
6. Khi VietQR cấp production, đổi base URL và credentials, nghiệm thu một giao dịch nhỏ thực tế theo quy trình của đơn vị vận hành.

Chưa chạy giao dịch ngân hàng thật trong quá trình phát triển vì chưa có credentials và domain callback của khách hàng. Kiểm thử tự động dùng mock provider; không thay thế nghiệm thu sandbox/production.

Tài liệu nguồn: [lấy token callback](https://api.vietqr.vn/vi/api-vietqr-callback/api-get-token), [tạo QR](https://api.vietqr.vn/vi/api-vietqr-callback/goi-api-generate-vietqr-code).
