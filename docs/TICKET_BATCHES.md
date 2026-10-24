# Quản lý lô vé và vé PDF

## Phạm vi hiện tại

Mã nội bộ do vticket tạo và kiểm soát. Smile PSM sẽ tích hợp ở giai đoạn sau; không đưa QR hiện tại vào cổng Smile để sử dụng thực tế. Hai hệ thống chưa đồng bộ tồn mã, trạng thái bán hay lượt vào cổng.

## Tạo và quản lý lô

1. Quản trị -> Quản lý vé: tạo loại vé/combo và giá. Dịch vụ có màn hình riêng, không quản lý lô.
2. Lô vé & mã vé -> Tạo lô vé: chọn loại vé, mã lô không trùng, số lượng (1–10.000) và khoảng ngày được sử dụng.
3. Mã sinh cố định theo dạng `MALO-000001`, `MALO-000002`… QR của mỗi mã là token ngẫu nhiên riêng, không chứa dữ liệu khách.
4. Xem tổng số, chưa bán, đang giữ, đã bán và đã dùng; mở danh sách mã, lọc trạng thái và phân trang 100 mã/trang.
5. Tạm dừng lô sẽ ngăn cấp mã cho đơn mới. Đơn đã giữ mã vẫn có thể hoàn tất trong thời gian giữ. Lô quá hạn sử dụng không cấp mã mới, số mã chưa bán vẫn được giữ trong lịch sử.

## Vòng đời mã

- AVAILABLE: chưa bán.
- RESERVED: giữ cho một đơn chờ thanh toán, tối đa 15 phút.
- SOLD: đã thanh toán, đã cấp vé nhưng chưa sử dụng.
- USED: đã qua kiểm soát vé vticket.

Số đã bán ở bảng lô = SOLD + USED. Phải chọn ngày dùng nằm trong hiệu lực lô. Kho mã dùng chung cho online/tại quầy; dịch vụ mua thêm không trừ kho. Đặt nhiều loại vé sẽ giữ tất cả mã trong cùng giao dịch, thiếu một loại thì không giữ phần còn lại.

Thanh toán dùng đúng các mã đã giữ; xác nhận thanh toán lặp không tạo vé mới. Hủy/hết hạn đơn trả mã về kho. Mã đã hết thời gian giữ có thể được đơn khác giữ lại; hủy đơn cũ không giải phóng mã của đơn mới. Khóa ghi danh mục theo thứ tự ID trước khi cấp mã để tránh bán trùng giữa các giao dịch đồng thời.

## PDF và kiểm soát

Sau khi thanh toán, trang đơn hiển thị vé và nút Tải PDF vé. Backend xuất file PDF thật (không phải chỉ mở hộp thoại in), mỗi vé một trang A6, có ngày dùng, mã vé, lô, mã đơn và QR. Font tiếng Việt được nhúng, QR có vùng trắng bao quanh. Mỗi QR cấp riêng một lượt, có thể quét bằng máy đọc USB hoặc nhập mã vé tại màn hình kiểm soát của vticket. Chỉ nhận đúng ngày dùng và đơn đã thanh toán; lần quét tiếp theo bị từ chối.

Dịch vụ mua thêm vẫn thể hiện trên đơn, không sinh mã hay trang vé riêng. Tải PDF chỉ cho đơn PAID và cần mã tra cứu bí mật của đơn.

## Cập nhật database hiện có

Flyway V2 thêm bảng kho/lô và trường liên kết, không xóa vé hay đơn cũ. Vé đã thanh toán từ bản trước tiếp tục tra cứu, xuất PDF và check-in được; chúng không được tính vào lô mới. Đơn chưa thanh toán từ bản trước chưa giữ mã kho: hủy và tạo đơn mới sau khi thêm lô.

**Sau khi nâng cấp, tạo lô cho từng loại vé trước khi bán.** Database demo mới có lô mẫu 100 mã/loại; database đã có danh mục không tự sinh lô để tránh tạo nhầm mã trên dữ liệu khách hàng.

Ngày dùng và hạn lô theo ngày Việt Nam. Mốc giữ/bán/check-in lưu UTC. Đơn giới hạn 500 vé để giữ PDF và thao tác cấp mã ở quy mô phù hợp; đây là giới hạn mỗi đơn, không phải hạn mức khách theo ngày/giờ.
