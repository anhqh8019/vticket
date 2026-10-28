# Tích hợp Smile Onsen — đồng bộ ngay khi tạo lô

## Phạm vi

vticket dùng một kết nối SQL Server riêng để ghi mã lô vào `[SMILE_ONSEN].[dbo].[TicketOnsen]`. Theo lựa chọn nghiệp vụ, tạo lô sẽ xếp hàng toàn bộ mã, kể cả chưa bán. Chỉ INSERT bản ghi chưa có; không UPDATE/DELETE vé Smile và không chạy Flyway/DDL trên database Smile.

Tính năng mặc định tắt. Chưa có kết nối/kiểm thử trên database Smile thật của khách hàng. Cổng Smile có thể cần quy tắc kích hoạt/bán riêng; việc bảng đã có dòng không chứng minh vé đã qua cổng được. Cần nghiệm thu với Smile trên môi trường thử trước khi triển khai thật.

## Hai cách ánh xạ định danh

CSV là dữ liệu chuẩn đã được khách hàng xác nhận: **BarCode=mã số, Serial=VN…**. Ảnh chụp trước đó bị sai, không dùng làm căn cứ ánh xạ. Mặc định SMILE_CODE_MAPPING=FIELD_NAMES:

| Chế độ                                              | TicketOnsen.BarCode    | TicketOnsen.Serial     |
| --------------------------------------------------- | ---------------------- | ---------------------- |
| VN_AS_BARCODE (tùy chọn cũ, không dùng cho CSV chuẩn) | Seri VN… của vticket   | Barcode số của vticket |
| FIELD_NAMES (mặc định, theo CSV chuẩn)                          | Barcode số của vticket | Seri VN… của vticket   |

Nếu môi trường đã đặt `SMILE_CODE_MAPPING=VN_AS_BARCODE`, cần đổi thành `FIELD_NAMES` và khởi động lại backend. Lô đã xếp hàng giữ ánh xạ cũ; thay đổi mặc định không tự sửa lô hoặc dữ liệu đã ghi sang Smile. Tạm dừng worker và đối soát các lô này trước khi tiếp tục, không thử lại theo ánh xạ sai.

Cách ánh xạ được chụp trên lô lúc xếp hàng, không tự đổi theo cấu hình sau đó. Barcode cổng trả về được lưu riêng ở `smileGateBarcode`; PDF sau bán dùng đúng barcode này. QR vticket vẫn có để dùng tại vticket; không khẳng định QR đó là QR Smile. Lô nhập từ Smile tiếp tục giữ nguyên barcode nguồn.

## Dữ liệu chuẩn đối chiếu

File CSV 100 bản ghi gần nhất xác nhận BarCode có 9–10 chữ số, Serial dạng VN015298 (VN + 6 chữ số). Giữ định danh dưới dạng chuỗi, không ép sang số nguyên 32 bit, không đổi hoặc cắt mã đã có. Ví dụ: BarCode=1171411923, Serial=VN015298. Toàn bộ mẫu có PkgCode=HK, IsWeekday=1, IsChild=0, Disabled=0; IsCancel, IsLoss, HotelID là NULL. Đây là dữ liệu của các lô mẫu, không phải mặc định cho mọi sản phẩm. Không suy quy tắc ngày thường hoặc cách cấp SeqNo từ CSV. Cách sinh Seri nội bộ hiện có không thay đổi trong bản sửa ánh xạ này.

## Các cột TicketOnsen

| Cột                                                          | Giá trị khi tạo mới                                                        |
| ------------------------------------------------------------ | -------------------------------------------------------------------------- |
| SeqNo                                                        | Do IDENTITY hoặc DEFAULT của Smile cấp; sau ghi đọc lại và lưu vào vticket |
| BarCode, Serial                                              | Theo chế độ ánh xạ trên                                                    |
| PkgCode                                                      | Điền mã loại vé Smile trên lô, không suy từ tên sản phẩm                   |
| FromDate, ToDate                                             | Thời hạn lô, ngày SQL lúc 00:00:00                                         |
| IsWeekday, IsChild                                           | Cờ đang lưu trên lô, mặc định false khi chưa đặt                           |
| Note                                                         | VTICKET + mã lô, cắt theo độ dài cột nếu cần                               |
| IsCancel, IsLoss, Disabled                                   | false cho bản ghi mới                                                      |
| CreateTime, LastChangeTime                                   | Thời gian tạo theo Asia/Ho_Chi_Minh                                        |
| CreateClerkID, LastChangeClerkID                             | SMILE_CLERK_ID nếu đã cấu hình; nếu chưa, để database áp DEFAULT/NULL      |
| HotelID                                                      | SMILE_HOTEL_ID nếu đã cấu hình; nếu chưa, để DEFAULT/NULL                  |
| CancelTime, CancelBy, CancelNote, LossTime, LossBy, LossNote | Không cấp giá trị, để DEFAULT/NULL của bảng                                |

Module đọc metadata thật để kiểm tra các cột bắt buộc, độ dài, nullability và cách sinh SeqNo. Nếu SeqNo không có IDENTITY/DEFAULT, module từ chối ghi và yêu cầu DDL/quy tắc cấp số từ Smile; **không dùng MAX+1**. Không tự sửa schema Smile. Nếu cột bắt buộc không có DEFAULT/NULL và chưa có cách cấp giá trị, kiểm tra kết nối sẽ báo rõ tên cột.

## Cấu hình backend

`.env` cho Docker Compose; chạy Maven/Windows service phải đặt cùng biến trong môi trường tiến trình. Không gửi mật khẩu trong chat hoặc commit vào git.

| Biến                                  | Nội dung                                                          |
| ------------------------------------- | ----------------------------------------------------------------- |
| SMILE_ENABLED                         | true để bật                                                       |
| SMILE_DB_URL                          | JDBC SQL Server với databaseName=SMILE_ONSEN, không chứa mật khẩu |
| SMILE_DB_USERNAME / SMILE_DB_PASSWORD | Tài khoản riêng của kết nối Smile                                 |
| SMILE_SCHEMA                          | dbo mặc định                                                      |
| SMILE_TABLE                           | TicketOnsen mặc định                                              |
| SMILE_CODE_MAPPING                    | FIELD_NAMES (mặc định); VN_AS_BARCODE chỉ dành cho cấu hình cũ                                    |
| SMILE_HOTEL_ID                        | Đúng HotelID Smile nếu cần                                        |
| SMILE_CLERK_ID                        | Đúng mã nhân viên/tích hợp Smile nếu cần                          |
| SMILE_WORKER_ENABLED                  | true mặc định, false để tạm dừng worker khi bảo trì               |

Ví dụ URL (thay host/port thực tế): `jdbc:sqlserver://<host>:1433;databaseName=SMILE_ONSEN;encrypt=true;trustServerCertificate=false`. Dùng chứng chỉ tin cậy khi triển khai thật. Chỉ bật trustServerCertificate=true cho môi trường thử nghiệm đã xác định. Tài khoản cần đọc metadata/SELECT và INSERT trên đúng bảng; không cần quyền xóa, sửa vé hoặc đổi cấu trúc. Không dùng tài khoản sa khi vận hành.

DB_URL/DB_USERNAME/DB_PASSWORD vẫn trỏ database **VTICKET** như trước. Không đổi các biến chính sang SMILE_ONSEN; Flyway V7 chỉ chạy trên database vticket để thêm hàng đợi và dấu đồng bộ.

## Cách dùng

1. Cấu hình và khởi động lại backend. Quản trị → Smile Onsen → kiểm tra mapping, bấm Kiểm tra kết nối và cấu trúc bảng. Bước này chỉ đọc metadata, không ghi vé thử.
2. Tạo lô nguồn vticket, chọn đúng loại vé gốc và điền PkgCode Smile. Toàn bộ mã vào hàng đợi PENDING, lô tạm dừng bán. Chưa cần thanh toán.
3. Worker chạy mỗi 5 giây, trạng thái SYNCING → SYNCED hoặc FAILED. Khi SYNCED, kiểm tra trên Smile rồi chủ động Mở bán lô.
4. Muốn đồng bộ lô cũ: Lô vé & mã vé → Xem mã → Đồng bộ lô sang Smile. Chỉ lô chưa có mã giữ/bán/dùng và đã có đủ Seri/barcode mới được xếp hàng. Nếu có lỗi kết nối, giữ nguyên PkgCode và bấm Xếp hàng / thử lại.
5. Cổng Smile hiện nhận thời hạn lô FromDate/ToDate, không tự nhận ngày sử dụng riêng của từng đơn vì lô được đẩy trước khi bán. Muốn Smile chặn đúng ngày đặt trên web cần thêm luồng cập nhật/kích hoạt sau bán, chưa triển khai trong module này.

Khi bán, mỗi vé giữ SeqNo Smile và barcode cổng đã đồng bộ; PDF gộp hoặc PDF trong ZIP dùng cùng barcode. Không ghi thêm bản TicketOnsen khi thu tiền.

Khi bật tích hợp, lô vticket cũ chưa SYNCED không được dùng để tạo đơn mới. Xử lý/hủy các đơn pending cũ trước khi bật; nếu có callback tiền đến nhưng mã chưa đồng bộ, đưa vào đối soát thay vì cấp vé chưa đăng ký. Không cố đẩy lô đã bán rồi đổi barcode của các PDF đã giao khách; cần quy trình chuyển đổi riêng.

Lô nhập từ Smile được coi là nguồn mã đã tồn tại, không INSERT lại vào Smile. Người vận hành vẫn phải kiểm tra tình trạng mã/nguồn và dành riêng kho cho kênh web theo quy trình hiện tại.

## Lỗi và chống trùng

Một lô được ghi trong một transaction Smile; lỗi giữa chừng rollback toàn bộ lô. Query dùng tham số và khóa giao dịch SERIALIZABLE. Nếu đã commit ở Smile nhưng mất kết nối trước khi vticket ghi nhận, lần thử lại đối chiếu đúng cặp BarCode/Serial, PkgCode, ngày và cờ thay vì INSERT lại. Dữ liệu có cùng một mã nhưng khác trường còn lại hoặc cờ hủy/mất/disabled được đưa vào FAILED để đối soát, không ghi đè.

Hàng đợi vticket nằm trong cùng transaction tạo lô, nên không có lô thành công nhưng mất sự kiện đồng bộ. Worker không giữ transaction vticket trong lúc kết nối Smile. Có lease 15 phút và backoff khi lỗi; khởi động lại vẫn tiếp tục được. Không thay PkgCode cho lô đã có lần đồng bộ để tránh remap các bản ghi có thể đã commit.

Trạng thái SYNCED là kết quả lần đối chiếu gần nhất, không phải giám sát thời gian thực mọi thay đổi bên Smile. Chưa đồng bộ lượt vào cổng, hủy, báo mất, hoàn tiền hoặc doanh thu vì bảng được cung cấp chưa chỉ ra đầy đủ các luồng/bảng liên quan.

## API quản trị

Tất cả cần ADMIN; POST cần CSRF như các thao tác ứng dụng khác.

- GET `/api/admin/smile/status`: bật/tắt, đã khai báo đủ kết nối, schema/table, chế độ ánh xạ; không trả password/URL chứa credentials.
- POST `/api/admin/smile/connection-test`: đọc metadata và kiểm tra điều kiện ghi, không INSERT.
- GET `/api/admin/smile/jobs`: 200 lô đồng bộ gần nhất, số lần thử, lỗi đã lọc thông tin kết nối.
- POST `/api/admin/smile/batches/{id}/sync`: `{ "pkgCode": "HK", "typeName": "Hoàng Kim" }`, xếp hàng/thử lại lô đủ điều kiện.
- POST `/api/admin/ticket-batches`: thêm `smileTicketTypeCode`, `smileTicketTypeName` khi nguồn vticket. PkgCode bắt buộc khi SMILE_ENABLED=true.
