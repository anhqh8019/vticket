# Tích hợp Smile PSM / Onsen

Đã có module ghi lô trực tiếp vào TicketOnsen khi tạo lô, kèm hàng đợi, kiểm tra schema, thử lại và đối chiếu mã trùng. Xem [hướng dẫn cấu hình và giới hạn](SMILE_ONSEN_SETUP.md). Các ghi chú trước khi có module được giữ dưới đây làm bối cảnh; hướng dẫn mới là SMILE_ONSEN_SETUP.md.

# Lô vé và tích hợp Smile PSM

## Yêu cầu đã nhận

- Quản lý loại vé/combo riêng, dịch vụ riêng. Dịch vụ không quản lý lô/mã vé.
- Vé có lô và danh sách mã, theo dõi mã còn lại, mã đã bán và mã đã sử dụng.
- Sau thanh toán, khách nhận file PDF chứa QR của từng vé để quét qua cổng.
- Mã vé hiện đang được quản lý trong Smile PSM.

## Đã thực hiện

Tách hai màn hình quản trị loại vé/combo và dịch vụ. Thêm lô vé nội bộ, theo dõi từng mã và xuất PDF sau thanh toán. Mã trong danh mục là mã loại vé, khác với mã riêng của từng vé vào cổng. Dữ liệu hiện có được giữ nguyên. Xem TICKET_BATCHES.md.

## Nhập lô từ Smile (V4)

Quản trị → Lô vé & mã vé → Tạo lô vé → Nguồn: Nhập Seri / barcode đã có từ Smile.

| Phạm vi   | Trường lưu                               | Ý nghĩa                                                       |
| --------- | ---------------------------------------- | ------------------------------------------------------------- |
| Lô        | code, productId                          | Mã lô nội bộ, loại vé/combo bán trên web                      |
| Lô        | source=SMILE                             | Nguồn dữ liệu, không phải trạng thái đồng bộ                  |
| Lô        | smileTicketTypeCode, smileTicketTypeName | Mã và tên loại vé gốc trên Smile                              |
| Lô        | validFrom, validUntil                    | Khoảng ngày sử dụng, gồm cả ngày cuối                         |
| Mỗi mã    | smileSerial                              | Seri gốc, ví dụ VN000945 trên vé mẫu                          |
| Mỗi mã    | smileBarcode                             | Barcode gốc dạng chuỗi, giữ số 0 đầu                          |
| Mỗi mã    | code, qrToken, state                     | Mã/QR nội bộ và trạng thái kho vticket                        |
| Vé đã cấp | source và bản chụp các trường Smile      | Giữ đúng cặp mã đã bán, loại vé Smile tại thời điểm phát hành |

Lấy **đúng mã loại vé từ dữ liệu Smile**, không suy từ tên in trên vé. Mỗi lô chỉ có một loại vé Smile và cùng thời hạn. Barcode không được tính từ Seri; không OCR hoặc lấy mã đã hết hạn trên ảnh mẫu làm kho thật.

Dán hai cột Seri và barcode từ Excel, hoặc dùng dấu tab/chấm phẩy/phẩy giữa hai cột, mỗi dòng một vé; dòng tiêu đề Seri–barcode là tùy chọn. Hai cột phải được giữ dạng Text từ nguồn để không mất số 0 đầu hoặc chuyển thành ký hiệu khoa học. Dữ liệu sau khi đã mất số không thể tự khôi phục. Mỗi lô 1–10.000 vé; số lượng lấy từ danh sách. Mã không chứa khoảng trắng, cho phép chữ/số và . _ / -; Seri tối đa 64, barcode tối đa 128 ký tự.

Kiểm tra trùng Seri và barcode trong danh sách và toàn bộ kho đã nhập, bao gồm vé đã bán/đã dùng. Bất kỳ dòng lỗi hoặc mã trùng đều hủy toàn bộ lần nhập, không tạo lô dở dang. Migration V4 chỉ thêm trường và unique index; dữ liệu/lô/vé cũ được giữ nguyên và vẫn hoạt động.

Lô Smile mới nhập **tạm dừng** để kiểm tra. Chỉ nhập mã chưa bán/chưa dùng đã được dành riêng cho web, rồi mở bán khi đã xác nhận quy trình vận hành. Mở bán trong vticket không kích hoạt mã bên Smile. Hiện chỉ cập nhật giữ/bán/dùng ở vticket; chưa truy vấn trạng thái Smile, chưa gửi giao dịch sang Smile, chưa phát barcode/QR theo giao thức cổng Smile. PDF vẫn là QR vticket. API danh sách mã trả các trường Smile để chuẩn bị bước tích hợp tiếp theo.

## Phương án cần xác nhận trước khi lập trình phần tích hợp

Smile PSM đang quản lý mã vé; chưa có tài liệu xác định hệ thống nào tạo lô, cấp mã, giữ mã và xác nhận sử dụng. Không giả định UUID đang sinh trong vticket được cổng Smile chấp nhận. Đã triển khai kho lô vé nội bộ vticket và PDF vé nội bộ; chưa triển khai cấp/đồng bộ mã qua Smile.

Luồng đề xuất để trao đổi: lấy lô/mã từ Smile hoặc tạo qua API được Smile hỗ trợ -> giữ số mã phù hợp cho đơn chờ thanh toán -> xác nhận bán/kích hoạt khi nhận thanh toán -> trả PDF chứa đúng nội dung QR Smile -> cổng kiểm tra và ghi nhận sử dụng -> đồng bộ trạng thái về vticket. Việc giữ/kích hoạt/đồng bộ phụ thuộc khả năng API thực tế.

Nếu dùng Excel/CSV thay API, cần xác nhận lô được dành riêng cho vticket và cách chuyển trạng thái đã bán/kích hoạt sang Smile. Chỉ import mã không đủ bảo đảm tránh bán trùng hoặc cho phép qua cổng.

Mô hình trạng thái dự kiến: còn bán, đang giữ, đã bán, đã sử dụng, bị hủy/hết hạn. Lô gồm mã lô, loại vé, thời hạn, nguồn Smile, tổng số và số lượng theo trạng thái. Mỗi mã cần nội dung QR thực, đơn/kênh bán, thời điểm bán và thời điểm qua cổng nếu Smile cung cấp.

## Thông tin cần cung cấp

1. Smile có API cấp/tra cứu/giữ/kích hoạt mã và trả trạng thái sử dụng không? Nếu không, có thể xuất Excel/CSV gồm những cột nào?
2. Một vé mẫu (có thể là vé thử), nội dung đọc từ QR và cách cổng Smile xác nhận vé hợp lệ.
3. Lô vé do Smile tạo hay yêu cầu vticket tạo rồi đăng ký sang Smile? Có bán cùng lô tại các kênh khác không?

Không gửi tài khoản/mật khẩu thật trong repository. Thông số API và thông tin xác thực sẽ cấu hình bằng biến môi trường khi đã xác định được giao thức.

## Ánh xạ theo ảnh CSDL Smile

| Cột Smile                                            | vticket                                       | Quy tắc hiện tại                                                                       |
| ---------------------------------------------------- | --------------------------------------------- | -------------------------------------------------------------------------------------- |
| SeqNo                                                | stock.smileSeqNo, ticket.smileSeqNo           | Lưu định danh nguồn dạng chuỗi; không dùng thay barcode                                |
| Serial                                               | stock.smileSerial                             | Giữ nguyên Seri                                                                        |
| BarCode                                              | stock.smileBarcode                            | Chuỗi, không làm tròn/mất số 0                                                         |
| PkgCode                                              | batch.smileTicketTypeCode                     | Mã gói/loại vé; tên lấy từ danh mục Smile, không suy tên từ HK                         |
| FromDate / ToDate                                    | batch.validFrom / validUntil                  | Mỗi lô cùng khoảng ngày; khác thời hạn phải tách lô                                    |
| IsWeekday / IsChild                                  | batch và ticket.smileIsWeekday / smileIsChild | Lưu cờ; lô có cờ true chưa được mở bán cho đến khi thống nhất quy tắc ngày dùng/trẻ em |
| IsCancel / IsLoss                                    | Kiểm tra khi nhập                             | Từ chối bản ghi true; chưa đồng bộ thay đổi phát sinh sau nhập                         |
| Note / CancelTime / CancelBy / CancelNote / LossTime | Chưa sao chép                                 | Không dùng làm căn cứ tự cập nhật Smile                                                |

Ngoài hai cột Seri/barcode, có thể dán ba cột theo thứ tự SeqNo–Serial–BarCode. Với kết quả SQL/Excel đầy đủ, **dán cả hàng tiêu đề**: parser nhận đúng tên cột và thứ tự thực tế (ví dụ SeqNo–BarCode–Serial). Nếu có PkgCode, FromDate, ToDate, IsWeekday, IsChild trong dữ liệu, các giá trị phải khớp cấu hình lô. Ngày dạng ISO YYYY-MM-DD hoặc YYYY-MM-DD 00:00:00.000; có giờ khác 00:00 không được tự bỏ qua. Cờ nhận 0/1 hoặc true/false; NULL ở cờ hủy/báo mất không được coi là true. Không tự suy trạng thái đã bán/đã dùng từ các cột đang thấy trong ảnh.

Bảng trong ảnh chưa hiển thị tên bảng đầy đủ, khóa chính và các cột bán/soát vé (nếu có), vì vậy chưa viết lệnh cập nhật trực tiếp CSDL Smile. Cần DDL hoặc danh sách cột và tài liệu luồng tích hợp trước khi đồng bộ. Bước hiện tại chỉ lưu dữ liệu nguồn trong vticket.
