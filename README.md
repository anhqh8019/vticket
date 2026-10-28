# Venusgiti vticket

Bản MVP React + Spring Boot 4.0.9 + Java 25 + SQL Server cho bán vé online và tại quầy, kế thừa giao diện Venusgiti đã thống nhất.

## Đã triển khai

- Tạo/sửa/ngừng bán vé, combo, dịch vụ mua thêm; gán dịch vụ bao gồm trong giá vé.
- Quản lý lô vé, sinh và theo dõi từng mã: chưa bán, đang giữ, đã bán, đã sử dụng. Dịch vụ không có lô.
- Đặt vé online và tại quầy, giữ mã từ cùng kho và lưu snapshot giá/thông tin khách.
- Cấp voucher riêng cho khách quen, giảm % tổng hóa đơn, có thời hạn, chỉ dùng một lần trên cả hai kênh; theo dõi và thu hồi.
- Giữ voucher theo đơn 15 phút, giải phóng khi hủy/hết hạn; kiểm soát đồng thời bằng khóa database.
- Thanh toán VietQR qua API và callback; tại quầy hỗ trợ thêm tiền mặt. Sau xác nhận thanh toán, tải PDF QR từng vé.
- Phát QR thật từ mã đã giữ, tải file PDF vé có font tiếng Việt (mỗi vé/trang), check-in đúng ngày và chống dùng lại trong vticket. Chưa kết nối cổng Smile PSM.
- Admin/staff, CSRF, nhật ký, tra cứu đơn bằng mã bí mật, thống kê trên 200 đơn gần nhất.

## Chạy nhanh để thử giao diện và nghiệp vụ

Cần JDK 25, Maven 3.9+, Node.js 22.12+.

Terminal 1:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Terminal 2:

```bash
cd frontend
npm ci
npm run dev
```

Mở http://localhost:5173. Demo dùng H2 file `backend/data`, chỉ nhằm chạy thử; hệ thống SQL Server dùng profile mặc định bên dưới.

Tài khoản **chỉ dành cho demo local**:

| Vai trò            | Tài khoản | Mật khẩu demo        |
| ------------------ | --------- | -------------------- |
| Quản trị           | admin     | demo-admin-change-me |
| Lễ tân / kiểm soát | staff     | demo-staff-change-me |

Đổi bằng ADMIN_PASSWORD và STAFF_PASSWORD. Không dùng các mật khẩu demo khi triển khai công khai. Tài khoản quản trị ban đầu chưa có màn hình đổi mật khẩu/quản lý nhân viên.

## Chạy SQL Server với Docker Compose

Cần Docker Engine/Desktop và Compose v2, máy x86_64, đủ RAM cho SQL Server (nên từ 8 GB). Image SQL Server Developer chỉ để phát triển/thử nghiệm.

1. Sao chép `.env.example` thành `.env`.
2. Điền ba mật khẩu khác nhau `SQLSERVER_SA_PASSWORD`, `ADMIN_PASSWORD`, `STAFF_PASSWORD` (tối thiểu 12 ký tự; SA cần chữ hoa/thường, số và ký tự đặc biệt). Không commit `.env`.
3. Chạy:

```bash
docker compose up --build -d
docker compose logs -f backend
```

4. Mở http://localhost:8088, đăng nhập admin/staff với mật khẩu đã đặt.

Compose tự tạo DB `vticket`, chạy Flyway và có thể nạp danh mục mẫu bằng APP_SEED_DATA=true khi DB trống. SQL Server lưu ở named volume, `docker compose down` giữ dữ liệu. Không dùng `down -v` nếu cần giữ DB.

Compose mặc định tắt VietQR cho đến khi cấu hình tài khoản API và callback. Có thể thử bán tiền mặt tại quầy ngay. Để bật chuyển khoản online/tại quầy, xem [hướng dẫn VietQR](docs/VIETQR_SETUP.md). Không còn endpoint thanh toán giả lập, kể cả profile demo.

Các port chỉ bind localhost. Khi triển khai LAN/Internet cần cấu hình reverse proxy, HTTPS và tài khoản DB riêng thay vì SA. `trustServerCertificate=true` trong Compose chỉ phục vụ chứng chỉ tự ký của SQL Server local.

## Kết nối SQL Server sẵn có, không Docker

Tạo database `vticket` và login có quyền migration trên database đó. Cấu hình trong terminal chạy backend, ví dụ PowerShell:

```powershell
$env:DB_URL='jdbc:sqlserver://localhost:1433;databaseName=vticket;encrypt=true;trustServerCertificate=true'
$env:DB_USERNAME='vticket'
$env:DB_PASSWORD='<mat-khau-database>'
$env:ADMIN_PASSWORD='<mat-khau-admin-tu-12-ky-tu>'
$env:STAFF_PASSWORD='<mat-khau-staff-tu-12-ky-tu>'
$env:APP_SEED_DATA='true'
cd backend
mvn spring-boot:run
```

Chỉ dùng trustServerCertificate=true trong môi trường thử nghiệm. Production dùng chứng chỉ tin cậy và giá trị false. Bỏ profile demo khi chạy SQL Server; database không tự tạo bởi backend.

## Kiểm thử

```bash
cd backend
mvn test
mvn package
cd ../frontend
npm ci
npm run build
```

Bộ test tích hợp H2 kiểm tra giảm giá cả vé + dịch vụ, hạn dùng/chủ voucher, giữ/giải phóng, dùng đồng thời, idempotent phát vé, check-in đồng thời, trạng thái đơn, phân quyền và CSRF. H2 không thay thế kiểm thử SQL Server. GitHub Actions có thêm job SQL Server thực khi workflow được bật.

## Bài thử nghiệm khách hàng

1. Admin -> Quản trị -> Vé & dịch vụ: tạo dịch vụ, tạo vé/combo và gán dịch vụ bao gồm.
2. Voucher khách hàng: cấp giảm 15%, nhập số điện thoại và hiệu lực bao trùm hiện tại; sao chép mã.
3. Online: chọn 2 vé + dịch vụ, nhập đúng số điện thoại và mã. Kiểm tra giảm 15% tổng đơn.
4. Trước khi thanh toán, thử dùng lại voucher ở quầy: phải bị từ chối đang giữ.
5. Tại quầy: xác nhận thu tiền mặt, nhận QR từng vé và tải PDF. Online/VietQR: cấu hình theo VIETQR_SETUP.md, chỉ nhận vé khi callback hợp lệ báo có.
6. Check-in cùng một QR hai lần: lần sau bị từ chối. QR chỉ vào đúng ngày sử dụng.
7. Thử lại voucher đã thanh toán: phải bị từ chối. Hủy đơn chưa thanh toán thì voucher dùng lại được.

## Giới hạn bản đầu

Chưa có email/SMS/Zalo, OTP xác minh số điện thoại, hóa đơn điện tử, hoàn tiền/đổi ngày, quét camera, quản lý nhân viên qua giao diện hoặc báo cáo đầy đủ. Voucher hiện kiểm tra **mã bí mật + số điện thoại khớp**, chưa xác minh chủ số bằng OTP. Các mục này cần hoàn thiện trước vận hành thương mại.

Xem [kết quả kiểm tra](docs/VALIDATION.md), [kiến trúc và quy tắc](docs/ARCHITECTURE.md), [API](docs/API.md).

## Cập nhật Java 25 và Spring Boot 4

Backend build và chạy bằng JDK 25. Docker build/runtime và GitHub Actions đều dùng Java 25. Spring Boot 4 dùng starter Web MVC/Flyway/test mới; dữ liệu và migration SQL Server được giữ nguyên.

Sau khi cập nhật code trên Windows:

```powershell
git pull origin main
java -version
mvn -version
cd backend
mvn clean verify
mvn spring-boot:run
```

Cả `java -version` và Java version trong `mvn -version` phải hiển thị 25. Nếu Maven còn dùng JDK cũ, chỉnh JAVA_HOME tới thư mục JDK 25 và mở lại terminal. Các biến DB_URL, DB_USERNAME, DB_PASSWORD, ADMIN_PASSWORD và STAFF_PASSWORD giữ cách cấu hình như trên. Nâng Java/Spring Boot không tự xử lý chứng chỉ SQL Server; local tự ký vẫn cần cấu hình trustServerCertificate phù hợp.

## Lô vé và tải PDF

Sau khi nâng cấp, Flyway V2 giữ nguyên vé/đơn cũ và bổ sung kho mã. Vào Quản trị -> Lô vé & mã vé để tạo lô cho từng loại vé trước khi bán. Đơn thiếu mã còn bán sẽ bị từ chối; dịch vụ không cần lô. Vé đã thanh toán từ bản trước vẫn dùng được. Đơn chưa thanh toán từ bản trước cần hủy và tạo lại vì chưa giữ mã từ kho.

Xem [hướng dẫn lô vé và PDF](docs/TICKET_BATCHES.md). Smile sẽ tích hợp sau; QR nội bộ chỉ dùng với màn hình kiểm soát vticket, chưa mở được cổng Smile.

## Thanh toán VietQR

Khách online chỉ chuyển khoản VietQR; nhân viên bán tại quầy chọn tiền mặt hoặc VietQR. Backend lấy token và tạo QR động từ API VietQR; callback xác thực riêng xác nhận tiền vào, sau đó cấp đúng mã vé đã giữ và mở tải PDF. Không xác nhận từ nút bấm của khách. Flyway V3 bổ sung giao dịch và danh sách đối soát; giữ nguyên dữ liệu cũ.

Cấu hình, URL callback, kiểm tra sandbox và xử lý chuyển khoản sai/hết hạn: [docs/VIETQR_SETUP.md](docs/VIETQR_SETUP.md). Tích hợp Smile PSM vẫn để giai đoạn sau.

## Chuẩn bị dữ liệu Smile

Màn hình lô vé hỗ trợ **nhập Seri / barcode gốc từ Smile**, mã/tên loại vé Smile, thời hạn và liên kết loại vé bán trên web. Giữ nguyên số 0 đầu, kiểm tra trùng và lưu đúng mã theo vé đã bán. Lô nhập mới tạm dừng để kiểm tra; chưa đồng bộ API hoặc soát QR qua cổng Smile. Xem [hướng dẫn nhập lô Smile](docs/SMILE_INTEGRATION.md).

## Vé và combo

Quản trị tách tab Vé / Combo / Dịch vụ. Combo tham chiếu một loại vé, cấu hình số vé cần mua và số vé tặng. Khách nhập số vé mua theo bội số nhóm; hệ thống chặn số chưa đủ, tính đơn giá mỗi vé mua và lấy cả vé tặng từ kho vé gốc. **Sau nâng cấp V6, cấu hình lại combo cũ trước khi mở bán**; không suy điều kiện từ tên/mô tả và không dùng lô combo cũ để thay kho vé gốc. Xem docs/API.md.

## Combo đại lý 300 / 500 vé

Đã hỗ trợ cấu hình và mua combo 300/500 vé. Sau thanh toán, khách tải ZIP gồm các PDF riêng (mỗi vé một file có Seri/barcode/QR) để phân phối lại. Giới hạn thực tế mỗi đơn 1.000 vé gồm vé tặng; mỗi vé giữ ngày dùng theo đơn gốc và chỉ sử dụng một lần. Trang chỉ xem trước 20 vé để tránh tải hàng trăm ảnh QR; ZIP luôn chứa toàn bộ vé.
