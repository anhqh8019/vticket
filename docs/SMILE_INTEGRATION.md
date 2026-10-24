# Lô vé và tích hợp Smile PSM

## Yêu cầu đã nhận

- Quản lý loại vé/combo riêng, dịch vụ riêng. Dịch vụ không quản lý lô/mã vé.
- Vé có lô và danh sách mã, theo dõi mã còn lại, mã đã bán và mã đã sử dụng.
- Sau thanh toán, khách nhận file PDF chứa QR của từng vé để quét qua cổng.
- Mã vé hiện đang được quản lý trong Smile PSM.

## Đã thực hiện

Tách hai màn hình quản trị loại vé/combo và dịch vụ. Thêm lô vé nội bộ, theo dõi từng mã và xuất PDF sau thanh toán. Mã trong danh mục là mã loại vé, khác với mã riêng của từng vé vào cổng. Dữ liệu hiện có được giữ nguyên. Xem TICKET_BATCHES.md.

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
