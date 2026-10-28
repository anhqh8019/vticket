# API v0.1

JSON. Các mutation cần CSRF token lấy từ GET `/api/csrf`, gửi header `X-XSRF-TOKEN` và cookie tương ứng. Nhân viên dùng HTTP Basic, HTTPS khi vận hành. Lỗi nghiệp vụ có `message` tiếng Việt.

| Method   | Path                                          | Quyền             | Nội dung                        |
| -------- | --------------------------------------------- | ----------------- | ------------------------------- |
| GET      | /api/public/config                            | Khách             | Trạng thái VietQR / môi trường  |
| GET      | /api/public/products                          | Khách             | Danh mục đang bán               |
| POST     | /api/public/orders                            | Khách             | Đơn ONLINE                      |
| GET      | /api/public/orders/{accessToken}              | Có token          | Đơn và vé                       |
| POST     | /api/public/orders/{accessToken}/cancel       | Có token          | Hủy pending                     |
| POST     | /api/public/orders/{accessToken}/vietqr       | Có token          | Tạo / lấy lại QR VietQR         |
| GET      | /api/public/orders/{accessToken}/vietqr/image | Có token, pending | PNG từ payload do VietQR trả về |
| POST     | /api/public/orders/{accessToken}/confirm-zero | Có token          | Xác nhận đơn tổng 0 đồng        |
| GET      | /api/admin/payment-receipts                   | Admin             | 200 giao dịch VietQR gần nhất   |
| GET      | /api/public/qr/{qrToken}                      | Có token          | PNG QR                          |
| GET      | /api/me                                       | Nhân viên         | Tài khoản/role                  |
| GET/POST | /api/staff/orders                             | Staff/Admin       | Danh sách / tạo COUNTER         |
| POST     | /api/staff/orders/{id}/cash                   | Staff/Admin       | Xác nhận đã thu tiền mặt        |
| POST     | /api/staff/orders/{id}/cancel                 | Staff/Admin       | Hủy pending                     |
| POST     | /api/staff/check-in                           | Staff/Admin       | Body: qrToken                   |
| GET/POST | /api/admin/products                           | Admin             | Danh sách / tạo vé, dịch vụ     |
| PUT      | /api/admin/products/{id}                      | Admin             | Sửa giá, dịch vụ, trạng thái    |
| GET/POST | /api/admin/vouchers                           | Admin             | Danh sách / cấp voucher         |
| POST     | /api/admin/vouchers/{code}/revoke             | Admin             | Thu hồi chưa dùng/chưa giữ      |
| GET      | /api/admin/audit                              | Admin             | Nhật ký gần nhất                |

Tạo đơn:

```json
{
  "customerName": "Khách thử",
  "phone": "0900000000",
  "email": "",
  "visitDate": "2026-12-01",
  "voucherCode": "",
  "lines": [{ "productId": 1, "quantity": 2 }]
}
```

ID thực lấy từ catalog. Phải có ít nhất một dòng TICKET/COMBO. Giá/subtotal/discount/total không nhận từ client.

Cấp voucher:

```json
{
  "customerName": "Khách thân quen",
  "phone": "0900000000",
  "percentage": 15,
  "validFrom": "2026-10-24T00:00:00Z",
  "validUntil": "2026-12-31T16:59:59Z",
  "note": "Chương trình khách quen"
}
```

Mốc thời gian ISO UTC; frontend datetime-local chuyển theo múi giờ máy người dùng. Ngày dùng vé được kiểm tra theo Asia/Ho_Chi_Minh. Mã voucher do server sinh, không cho sửa % hay chủ sở hữu sau phát hành; thu hồi và tạo mã mới khi cần.

## Lô vé và PDF

| Method   | Path                                                        | Quyền             | Nội dung                             |
| -------- | ----------------------------------------------------------- | ----------------- | ------------------------------------ |
| GET/POST | /api/admin/ticket-batches                                   | Admin             | Danh sách thống kê / tạo lô và mã    |
| GET      | /api/admin/ticket-batches/{id}/codes?state=AVAILABLE&page=0 | Admin             | Lọc mã theo trạng thái, 100 mã/trang |
| PUT      | /api/admin/ticket-batches/{id}/active                       | Admin             | Bật/tạm dừng lô, body active         |
| GET      | /api/public/orders/{accessToken}/tickets.pdf                | Có token đơn PAID | Tải PDF thực tế                      |

Body tạo lô:

```json
{
  "code": "TAMKHOANG-2026-01",
  "productId": 3,
  "quantity": 100,
  "validFrom": "2026-10-24",
  "validUntil": "2027-10-24"
}
```

Không tạo lô cho SERVICE. Tạo đơn phải có đủ mã của lô đang mở bán và chứa ngày sử dụng. Đơn tối đa 500 vé. PDF chưa hỗ trợ QR Smile; nguồn mã hiện là VTICKET.

## Callback VietQR

Hai endpoint server-to-server dùng cơ chế riêng, không dùng tài khoản nhân viên và không cần CSRF:

| Method | Path                              | Xác thực                   | Nội dung                             |
| ------ | --------------------------------- | -------------------------- | ------------------------------------ |
| POST   | /vietqr/api/token_generate        | Basic callback credentials | access_token, token_type, expires_in |
| POST   | /vietqr/bank/api/transaction-sync | Bearer callback JWT        | Ghi nhận báo có / đối soát           |

Alias tương thích: `/api/vietqr/token_generate`, `/api/vietqr/transaction-sync`, `/api-vietqr-callback/api-get-token`, `/api-vietqr-callback/api-transaction-sync`. Chi tiết body và cấu hình xem [VIETQR_SETUP.md](VIETQR_SETUP.md).

GET đơn trả `{order, tickets, payment}`; `payment` là null khi chưa tạo VietQR, hoặc chứa orderId (mã VietQR 13 ký tự), state, amount, bankCode, bankAccount, accountName, virtualAccount, content, expiresAt, message. Khách poll mỗi 3 giây khi pending. Tiền mặt chỉ cho đơn COUNTER, STAFF/ADMIN, chưa chọn VietQR. PDF chỉ trả khi PAID. Endpoint `demo-pay` đã xóa.

## Nhập lô Smile

`POST /api/admin/ticket-batches/import-smile` (ADMIN + CSRF):

```json
{
  "code": "SMILE-WEB-01",
  "productId": 3,
  "smileTicketTypeCode": "MA_LOAI_TU_SMILE",
  "smileTicketTypeName": "Tên loại vé trên Smile",
  "validFrom": "2026-10-27",
  "validUntil": "2027-10-27",
  "codes": [
    { "serial": "TEST000001", "barcode": "0000123456789" },
    { "serial": "TEST000002", "barcode": "0000123456790" }
  ]
}
```

Mã trong ví dụ là dữ liệu minh họa, không phải mã Smile hợp lệ. Không gửi quantity: server tính theo codes. Hai cờ smileIsWeekday/smileIsChild mặc định false; cờ true chỉ cho lưu lô tạm dừng. Mỗi phần tử codes có thể thêm seqNo (chuỗi số tối đa 19 ký tự), pkgCode, fromDate, toDate, isWeekday, isChild, isCancel, isLoss; server kiểm tra khớp cấu hình lô và từ chối mã hủy/báo mất. Response là TicketBatch với source=SMILE, active=false. Kiểm tra và mở bán qua endpoint active hiện có. Ngày hết hạn không được trước ngày hiện tại. Không cho nhập SERVICE. Trùng/vi phạm ràng buộc trả 409, body sai trả 400; toàn bộ import rollback khi có lỗi.

GET `/api/admin/ticket-batches/{id}/codes` giữ phân trang/bộ lọc cũ, bổ sung smileSerial, smileBarcode từng mã và smileTicketTypeCode/Name trên batch. GET đơn đã trả tiền bổ sung source, smileSerial, smileBarcode, smileTicketTypeCode/Name trên vé đã cấp. Không endpoint nào trong phần này thực hiện kết nối hoặc đồng bộ Smile.

## Combo tham chiếu loại vé

Product kind COMBO cần comboTicketId (ID một TICKET), comboBuyQuantity (1–1.000), comboGiftQuantity (0–1.000). price là **đơn giá mỗi vé mua**, không phải giá trọn combo. Vé, combo và dịch vụ được quản lý riêng; không đổi kind sau khi tạo.

Ví dụ price=450000, comboBuyQuantity=10, comboGiftQuantity=1: dòng đặt quantity=10 tính 4.500.000đ và cấp 11 vé; quantity=20 cấp 22 vé. quantity phải là bội số của comboBuyQuantity, tối đa 1.000; kiểm tra ở cả frontend và backend trước khi tạo đơn/chuyển sang thanh toán. Vé tặng không tính tiền nhưng giữ/cấp mã thật từ kho comboTicketId. Tổng mã thực tế một đơn tối đa 1.000, gồm vé tặng.

BookingLine lưu ticketProductId, ticketQuantity, ticketProductName tại thời điểm đặt. Thanh toán/giữ mã/phát hành theo snapshot này, không tính lại theo cấu hình combo hiện tại. Dịch vụ bao gồm là hợp của dịch vụ trên vé gốc và combo, không tự nhân thành dòng thu tiền thêm.

Chỉ tạo/nhập lô cho TICKET, không tạo lô riêng cho COMBO. Flyway V6 tạm dừng combo cũ do không thể suy số vé mua/tặng từ tên hoặc mô tả: quản trị vào tab Combo, chọn vé gốc, nhập điều kiện và kiểm tra lại đơn giá trước khi mở bán. Đơn/vé đã tạo trước nâng cấp giữ cơ chế snapshot cũ, không tự thay đổi số lượng.

## Combo đại lý và tải ZIP

Hỗ trợ nhóm mua 300 hoặc 500 vé, không cần tạo loại tài khoản mới. Giữ mô hình đơn giá mỗi vé mua; mua đủ bội số của comboBuyQuantity. Mỗi dòng tối đa 1.000; một nhóm comboBuyQuantity + comboGiftQuantity và tổng vé phát hành mỗi đơn đều tối đa 1.000, gồm vé tặng. Kho vé gốc phải đủ toàn bộ số mã.

GET `/api/public/orders/{accessToken}/tickets.zip`: chỉ trả ZIP khi đơn PAID và đã có vé, cùng quyền accessToken với PDF gộp. Pending/cancelled/expired bị từ chối, token sai trả 404. Header attachment, application/zip và Cache-Control: no-store. Mỗi ZIP entry là một PDF một trang cho đúng một vé, có Seri, barcode Code 128, QR và ngày sử dụng. Tên file theo thứ tự và Seri đã lọc ký tự; không chứa accessToken hay thông tin liên hệ khách. Tải lại không phát hành mã mới.

ZIP được ghi tuần tự ra response, tạo/đóng từng PDF để không gom 500 PDF vào bộ nhớ. Timeout async 5 phút. Trang đơn chỉ xem trước 20 vé với đơn lớn, nút ZIP tải đủ toàn bộ; PDF gộp vẫn giữ. Vé bán lại dùng đúng ngày đã đặt và vẫn chỉ được soát một lần. Không tự động thay đổi chủ đơn hoặc tạo quy trình thanh toán đại lý cấp hai.
