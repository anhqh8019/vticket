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

## Module Smile Onsen

Đã bổ sung kết nối SQL Server riêng và hàng đợi ghi lô ngay khi tạo vào dbo.TicketOnsen. Mặc định tắt; cấu hình trong [docs/SMILE_ONSEN_SETUP.md](docs/SMILE_ONSEN_SETUP.md). Có tab quản trị Smile Onsen để kiểm tra kết nối/schema và xem lỗi. Lô chờ/lỗi chưa mở bán; barcode PDF lấy theo mapping đã đồng bộ. Chưa kết nối database Smile thật của khách hàng hoặc đồng bộ trạng thái soát/hủy/hoàn tiền.

## Email vé sau thanh toán

Có thể bật gửi email nền cho khách đã nhập email: PDF đơn thường, ZIP từ 300 vé, hoặc link tải khi file quá lớn. Cấu hình SMTP và WinSW xem [EMAIL_SETUP.md](docs/EMAIL_SETUP.md). Lỗi email không làm mất thanh toán hoặc phát hành vé trùng.

### Phiên đăng nhập nhân viên

Giao diện đăng nhập qua `POST /api/auth/login` và giữ phiên bằng cookie
`JSESSIONID` HttpOnly. Khi tải lại trang, `/api/me` khôi phục tài khoản và quyền;
trình duyệt chỉ nhớ tên màn hình vận hành trong sessionStorage, không lưu mật khẩu.
`POST /api/auth/logout` hủy phiên. Đăng nhập/đăng xuất vẫn yêu cầu CSRF.
HTTP Basic vẫn được hỗ trợ cho công cụ gọi API trực tiếp; callback VietQR giữ
cơ chế JWT riêng, không dùng phiên nhân viên.

Thời gian chờ mặc định là **30 phút không hoạt động**. Có thể đặt biến môi trường
`SESSION_TIMEOUT=2h` trong tiến trình backend nếu cần phiên dài hơn. Phiên hiện
lưu trong bộ nhớ backend: khởi động lại backend sẽ yêu cầu đăng nhập lại.

Khi cập nhật tính năng này, triển khai **cả backend và frontend/dist**, khởi động
lại backend rồi đăng nhập một lần. Với Nginx HTTPS, giữ
`proxy_set_header X-Forwarded-Proto $scheme;` để backend nhận đúng HTTPS và đặt
cookie Secure. Chỉ cho truy cập backend qua proxy tin cậy, không mở công khai
cổng 8080 khi dùng forwarded headers. Frontend và API cần dùng cùng origin.

Kiểm tra sau triển khai: đăng nhập admin/nhân viên → mở màn hình vận hành → F5 →
vẫn đúng tài khoản/quyền; đăng xuất → F5 → không còn phiên; nhân viên không mở
được API quản trị. Cookie hết hạn, phiên không hoạt động quá thời gian chờ hoặc
backend khởi động lại thì cần đăng nhập lại.

### Quản lý người dùng (V9)

Đăng nhập quản trị → **Quản trị → Người dùng** để tạo tài khoản, cập nhật họ tên,
phân quyền ADMIN/STAFF, khóa/mở hoặc đặt lại mật khẩu. Nhân viên không truy cập
được chức năng này. Tên đăng nhập 3–100 ký tự không dấu, không phân biệt hoa/thường;
mật khẩu từ 12 ký tự và tối đa 72 byte UTF-8, lưu dưới dạng BCrypt.

Nâng cấp cả backend và frontend. Flyway V9 tự tạo `user_accounts` trên SQL Server
(và H2 demo). Khi bảng chưa có tài khoản, ứng dụng khởi tạo admin/nhân viên từ
`ADMIN_USER`, `ADMIN_PASSWORD`, `STAFF_USER`, `STAFF_PASSWORD` hiện có. Những lần
khởi động sau giữ nguyên tài khoản/mật khẩu trong CSDL: sửa biến môi trường
không đặt lại mật khẩu; hãy dùng màn hình Người dùng. Không xóa bảng tài khoản.

Khóa, đổi quyền hoặc đặt lại mật khẩu làm phiên cũ hết hiệu lực ở yêu cầu tiếp
theo. Không thể tự khóa/hạ quyền tài khoản đang dùng và phải giữ ít nhất một
quản trị viên hoạt động. Thao tác quản lý được ghi nhật ký, không ghi mật khẩu.

### Giá combo theo ngày sử dụng (V10)

Trong Tạo/Sửa combo, cấu hình giá ngày thường, cuối tuần và lễ Tết **trên một vé
mua**. Thứ Bảy/Chủ nhật là cuối tuần; ngày có trong lịch lễ ưu tiên giá lễ Tết.
Giá đặc biệt để trống dùng giá ngày thường; giá bằng 0 được giữ đúng là 0.
Loại vé lẻ cũng hỗ trợ giá cuối tuần/lễ Tết riêng; dịch vụ giữ giá cố định. Số vé tặng không tính tiền.

Quản trị → **Lịch lễ Tết** cho phép chọn năm, thêm/sửa/bỏ ngày và lưu lịch năm đó.
V10 nạp đúng 20 ngày năm 2027 do khách hàng cung cấp: 01–03/01, 07–10/02,
16–18/04, 30/04–03/05, 02–05/09, 24/11, 31/12. Đây là lịch kinh doanh tùy chỉnh;
những năm khác cần nhập riêng. Chỉ quản trị viên có quyền sửa; thao tác có nhật ký.

Khi mua online hoặc tại quầy, giao diện hiển thị giá theo ngày sử dụng được chọn;
backend tự tính lại và lưu đơn giá vào từng dòng đơn. Đơn đã tạo (kể cả đang chờ
thanh toán) giữ nguyên đơn giá sau khi sửa giá/lịch. Voucher vẫn giảm trên tổng
hóa đơn đã tính theo lịch. Cần cập nhật cả backend/frontend; Flyway tự chạy V10,
không sửa giá combo cũ.

Giá loại vé dùng cùng lịch và quy tắc ưu tiên như combo; cấu hình trong Tạo/Sửa
loại vé. Giá combo được cấu hình riêng, không bị giá loại vé tham chiếu ghi đè.
Nâng cấp phần này không cần migration mới nếu V10 đã chạy; cập nhật cả backend
và frontend. Vé cũ chưa có giá đặc biệt tiếp tục dùng giá ngày thường.

### Giá trẻ em tại quầy (V11)

Tạo/Sửa loại vé hoặc combo có ba giá trẻ em: ngày thường, cuối tuần, lễ Tết.
Giá trẻ em ngày thường để trống nghĩa là chưa bán mức giá trẻ em. Giá trẻ em
cuối tuần/lễ để trống dùng **giá trẻ em ngày thường**, không dùng giá người lớn.
Quy tắc ngày sử dụng, ưu tiên lễ Tết và vé tặng giữ nguyên.

Chỉ màn hình Bán tại quầy có chọn Người lớn/Trẻ em theo từng sản phẩm. Số lượng
mỗi nhóm được giữ riêng khi chuyển lựa chọn, cho phép cùng một đơn có cả hai
nhóm của cùng loại vé/combo. Combo mỗi nhóm vẫn phải đủ bội số. Khách online
không có lựa chọn và API public từ chối `child=true`, kể cả tự sửa request.
Đơn lưu nhóm giá/đơn giá; PDF đánh dấu vé trẻ em, giá cũ không bị đổi sau khi
sửa bảng giá. Cập nhật backend/frontend và chạy migration tự động V11.

Đây là nhóm **giá bán tại quầy**; việc cấp mã và đồng bộ Smile giữ theo loại vé,
lô đã cấu hình. Không tự sửa `TicketOnsen.IsChild` hoặc mã loại vé Smile khi chọn
giá trẻ em. Nếu Smile yêu cầu lô/mã riêng cho trẻ em, cần thiết lập luồng ánh xạ
kho riêng trước khi dùng nhóm giá này cho loại vé đó.

### Popup chi tiết vé/combo (V12)

Mỗi thẻ vé/combo tại màn hình đặt vé và bán tại quầy có **Xem chi tiết**. Popup
hiển thị ảnh trái, nội dung phải (điện thoại xếp dọc), giá theo ngày sử dụng và
nhóm người lớn/trẻ em đang chọn. Đóng bằng nút ×, Escape hoặc bấm ngoài popup.

Trong Tạo/Sửa loại vé hoặc combo → **Nội dung popup “Xem chi tiết”**, tải ảnh
PNG/JPG tối đa 2 MB, tối đa 6000 px mỗi cạnh và 16 megapixel; nhập nội dung tới
12.000 ký tự. Nội dung giữ xuống dòng, hiển thị dạng văn bản, không thực thi HTML.
Có xem trước và bỏ ảnh. Chưa có nội dung thì dùng mô tả ngắn; chưa có ảnh dùng
khung thương hiệu Venusgiti. Ảnh lưu trong CSDL, được sao lưu cùng database.

Nâng cấp backend/frontend để Flyway chạy V12. Nếu dùng Nginx, đặt trong khối
`server` hoặc `location /api/` rồi kiểm tra và reload Nginx:

```nginx
client_max_body_size 10m;
```

Ảnh được gửi dạng JSON base64 cùng sản phẩm (tối đa 3 × 2 MB ảnh ≈ 8,4 MB JSON), nên cần
mức giới hạn trên để tránh HTTP 413 khi tải ảnh lớn hơn mặc định của Nginx.

### Thư viện tối đa 3 ảnh (V13)

Tạo/Sửa vé hoặc combo có 3 ô ảnh theo thứ tự. Popup xếp các ảnh từ trên xuống
trong cột trái, căn phía trên; bỏ qua ô trống. Mỗi ảnh giữ giới hạn PNG/JPG 2 MB.
V13 giữ nguyên ảnh V12 ở vị trí đầu tiên và bổ sung hai vị trí mới. Có thể thay
hoặc bỏ riêng từng ảnh; sửa nội dung không làm mất ảnh đã lưu. Cập nhật backend
và frontend, nâng `client_max_body_size` của Nginx lên **10m** rồi reload để lưu
được cả ba ảnh dung lượng lớn trong một lần.

### Quy tắc mã mới (V14)

Mã sinh mới: `BarCode = TICKET_BARCODE_PREFIX + số tăng nội bộ`, mặc định prefix
`1131`, phần số ít nhất 6 chữ số (ví dụ `1131000001`). Bộ đếm lưu trong CSDL,
không reset khi đổi prefix hoặc khởi động lại. Cấu hình trong biến môi trường
backend, `.env` của Compose hoặc WinSW:

```xml
<env name="TICKET_BARCODE_PREFIX" value="1131" />
```

`Serial = VN + SeqNo thực tế do Smile cấp`, đệm đủ 10 chữ số sau VN: SeqNo 22924 →
VN0000022924. Trước đồng bộ, Seri hiển thị “Chờ SeqNo Smile”. Mã mới luôn dùng ánh xạ
FIELD_NAMES. Khi Smile tắt, chỉ có barcode nội bộ; không giả lập SeqNo/Seri.

Trong một transaction Smile: INSERT vé với Seri tạm duy nhất → lấy SeqNo do
IDENTITY/DEFAULT cấp → hoàn tất Serial=VN+SeqNo (đệm 10 chữ số) **chỉ cho dòng vừa INSERT** →
COMMIT. Tài khoản Smile cần SELECT/INSERT và UPDATE riêng cột Serial. Retry tìm
theo barcode và kiểm tra thông tin; không ghi đè dòng đã tồn tại nếu không khớp.
Không dùng MAX(SeqNo)+1, không sửa IDENTITY. Các lô/mã cũ và vé đã bán không tự
đổi mã; lô import Smile giữ nguyên thông tin nguồn. Cập nhật backend/frontend,
Flyway tự chạy V14 trên DB vticket, không chạy migration trên Smile.


### Vé tháng (Silver / Gold / Platinum)

- Chỉ bán tại quầy: menu **Vé tháng**. Admin vào **Thiết lập 3 hạng vé**, chọn vé gốc và giá một ngày thường / cuối tuần. Lễ Tết dùng lịch hàng năm và giá cuối tuần.
- Silver chỉ chọn T2–T6 không phải ngày lễ; Gold và Platinum được chọn cả cuối tuần/lễ. Platinum được nhân viên phát **1 phiếu ăn vật lý mỗi lần đến**, không tạo QR/mã kho vé ăn.
- Chọn các ngày ở **nhiều tháng/năm**, không chọn ngày đã qua (tối đa 30 ngày/thẻ, dùng hết/qua hết ngày đăng ký thì mua thẻ mới). Silver tối thiểu 12 ngày thường; Gold/Platinum cần ít nhất 1 ngày thứ Bảy hoặc Chủ nhật, ngày lễ rơi vào ngày thường không thay thế điều kiện này. Tổng tiền = tổng đơn giá của ngày đã chọn; đây là giá từng ngày, không phải giá trọn tháng cố định. Được mua trước cho tháng sau. Không tự động thêm ngày, gia hạn hay chuyển lượt dư.
- Đơn dùng luồng CASH/VietQR hiện có; chỉ sau PAID mới có QR/PDF thẻ, gửi email nếu đã bật SMTP và có email. Giá, hạng, ngày, loại vé được chốt tại lúc tạo đơn, sửa gói không thay đổi thẻ cũ.
- Máy quét QR dạng bàn phím: **Quét thẻ & cấp vé lượt**, quét rồi Enter. Nhân viên đối chiếu họ tên, xác nhận để cấp. Không có nhận diện khuôn mặt/CCCD tự động. Thẻ tháng không dùng trực tiếp tại Smile.
- Mỗi thẻ được cấp đúng một lượt/ngày đã chọn. Lấy vé trong lô đang bán, còn hạn ngày đến, đủ điều kiện đồng bộ Smile; Platinum yêu cầu nhân viên xác nhận phát phiếu ăn vật lý, ghi nhận cùng lần cấp vé. Cấp và ghi lượt trong cùng transaction; thiếu vé vào cổng thì rollback, không ghi đã phát phiếu. Quét lại trả đơn/vé lượt cũ, kể cả đã dùng tại cổng, không cấp thêm. Khóa thẻ cùng ràng buộc DB ngăn hai quầy cấp trùng.
- Kho chỉ được trừ **khi đến nhận vé**, không giữ sẵn mã cho mọi ngày khi bán thẻ tháng. Vận hành cần chuẩn bị đủ kho cho khách thẻ tháng. Ngày đã cấp được ghi là **đã cấp**, không suy diễn khách đã qua cổng Smile; việc đọc lịch sử cổng là luồng riêng.
- V15 tạo bảng monthly_plans, monthly_passes, monthly_pass_days trong DB VTicket; không đổi bảng/cổng Smile. Tiền doanh thu nằm trên đơn mua thẻ; đơn cấp lượt có tổng 0 và payment_method=MONTHLY_PASS để không cộng doanh thu hai lần.

Kiểm thử triển khai vé tháng:
1. Admin cấu hình đủ 3 hạng và tạo/mở bán lô vé vào cổng. Staff không được sửa cấu hình.
2. Silver chặn T7/CN/lễ; Gold/Platinum cho chọn; ngày lễ dùng giá cuối tuần. Chọn ngày qua nhiều tháng/năm, kiểm tra tổng tiền và PDF lịch đầy đủ; chặn ngày quá khứ/trùng, Silver dưới 12 ngày, Gold/Platinum thiếu T7/CN.
3. Đơn chờ thanh toán không có QR và không cấp lượt. Test CASH, VietQR callback, tải PDF và email sau thanh toán; callback lặp không cấp thêm thẻ/vé.
4. Quét thẻ đúng ngày → nhận vé mới. Quét lại, F5, hai quầy cùng cấp → cùng một đơn/vé. Thẻ chưa thanh toán, ngoài lịch hoặc hết hạn bị chặn.
5. Platinum yêu cầu xác nhận phát phiếu ăn vật lý → đúng 1 vé vào cổng, ghi nhận đã phát phiếu ăn. Quét lại không phát thêm phiếu. Thiếu vé vào cổng → không mất lượt/không ghi đã phát phiếu.
6. In PDF vé lượt, thử qua cổng Smile thật và đối chiếu quy trình phát phiếu ăn vật lý. Kiểm tra vé dùng một lần; QR thẻ tháng chỉ tra cứu/cấp tại quầy.

Thẻ tháng bắt buộc số điện thoại khi mua (vé lượt thông thường vẫn giữ quy định cũ). Số điện thoại hiển thị dưới tên trên thẻ/PDF. Màu thẻ: Silver xám bạc, Gold vàng nhạt, Platinum xanh nhạt.

Màn hình **Kiểm soát vé** nhận cả QR vé lượt và thẻ tháng. Thẻ tháng chỉ mở thông tin/xác nhận cấp vé, chưa trừ lượt khi quét; vé lượt vẫn check-in trên VTicket. Việc mở cổng/đánh dấu sử dụng bên Smile vẫn do hệ thống cổng Smile xử lý.
