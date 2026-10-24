# API v0.1

JSON. Các mutation cần CSRF token lấy từ GET `/api/csrf`, gửi header `X-XSRF-TOKEN` và cookie tương ứng. Nhân viên dùng HTTP Basic, HTTPS khi vận hành. Lỗi nghiệp vụ có `message` tiếng Việt.

| Method   | Path                                      | Quyền       | Nội dung                     |
| -------- | ----------------------------------------- | ----------- | ---------------------------- |
| GET      | /api/public/config                        | Khách       | Cấu hình demo                |
| GET      | /api/public/products                      | Khách       | Danh mục đang bán            |
| POST     | /api/public/orders                        | Khách       | Đơn ONLINE                   |
| GET      | /api/public/orders/{accessToken}          | Có token    | Đơn và vé                    |
| POST     | /api/public/orders/{accessToken}/cancel   | Có token    | Hủy pending                  |
| POST     | /api/public/orders/{accessToken}/demo-pay | Demo        | Thanh toán giả lập           |
| GET      | /api/public/qr/{qrToken}                  | Có token    | PNG QR                       |
| GET      | /api/me                                   | Nhân viên   | Tài khoản/role               |
| GET/POST | /api/staff/orders                         | Staff/Admin | Danh sách / tạo COUNTER      |
| POST     | /api/staff/orders/{id}/cash               | Staff/Admin | Xác nhận đã thu tiền mặt     |
| POST     | /api/staff/orders/{id}/cancel             | Staff/Admin | Hủy pending                  |
| POST     | /api/staff/check-in                       | Staff/Admin | Body: qrToken                |
| GET/POST | /api/admin/products                       | Admin       | Danh sách / tạo vé, dịch vụ  |
| PUT      | /api/admin/products/{id}                  | Admin       | Sửa giá, dịch vụ, trạng thái |
| GET/POST | /api/admin/vouchers                       | Admin       | Danh sách / cấp voucher      |
| POST     | /api/admin/vouchers/{code}/revoke         | Admin       | Thu hồi chưa dùng/chưa giữ   |
| GET      | /api/admin/audit                          | Admin       | Nhật ký gần nhất             |

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
