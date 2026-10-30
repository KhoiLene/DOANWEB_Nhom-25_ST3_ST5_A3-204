# 🛒 Techtra Shop — Đồ Án Web Nhóm 25

Website thương mại điện tử xây dựng bằng **Java Spring Boot 4**, **Thymeleaf SSR**, **PostgreSQL 16**, tích hợp **Docker Compose** tự động.

---

## 📦 Yêu cầu cài đặt trên máy

| Công cụ | Phiên bản | Link tải |
|---|---|---|
| **JDK (Java)** | 17 hoặc 21 | https://adoptium.net |
| **Docker Desktop** | Mới nhất | https://www.docker.com/products/docker-desktop |

> ⚠️ **Lưu ý quan trọng:**
> - Trên Windows, nút Run sẽ thử mở Docker Desktop nếu Docker chưa chạy. Hãy hoàn tất thiết lập Docker Desktop lần đầu trước khi chạy project.
> - Không cần cài PostgreSQL thủ công — Docker sẽ lo phần này.
> - Không cần cài Maven — đã có sẵn `mvnw` trong source code.

---

## 🚀 Hướng dẫn chạy lần đầu (sau khi clone)

### Bước 1 — Clone repo về máy

```bash
git clone <đường-dẫn-repo>
cd DOANWEB_Nhom-25_ST3_ST5_A3-204
```

### Bước 2 — Chuẩn bị Docker Desktop

Hãy cài và hoàn tất thiết lập **Docker Desktop**. Bạn có thể mở sẵn và đợi **"Engine running"**, hoặc để nút Run trong IntelliJ thử mở Docker Desktop trên Windows.

### Bước 3 — Chạy project

**Trong IntelliJ IDEA:** mở thư mục project bằng `pom.xml`, đợi Maven nạp xong, rồi mở
`src/main/java/com/example/demo/WebApplication.java` và bấm nút tam giác cạnh hàm `main`.
Chọn **Run 'WebApplication'**. Khi chạy tại máy cá nhân, ứng dụng sẽ bật Docker Desktop
(trên Windows, nếu cần), khởi động PostgreSQL và Qdrant, chờ cơ sở dữ liệu sẵn sàng rồi
mới mở website. Không cần chạy `docker compose up` thủ công. Nếu đã có phiên web khác
chiếm cổng 8080, hãy dừng phiên đó trước khi bấm Run.

**Trong terminal:**

Mở terminal (CMD / PowerShell) tại thư mục project rồi gõ:

**Windows:**
```cmd
.\mvnw clean spring-boot:run
```

**Mac / Linux:**
```bash
./mvnw clean spring-boot:run
```

> 💡 **Lần đầu chạy sẽ mất vài phút** vì Maven cần tải thư viện về. Từ lần 2 trở đi sẽ nhanh hơn nhiều.

### Bước 4 — Chờ dòng thông báo thành công

Khi thấy dòng này trong terminal là project đã sẵn sàng:

```
Started WebApplication in XX.XXX seconds
```

---

## 🌐 Truy cập

| Trang | Địa chỉ |
|---|---|
| **Trang chủ (người dùng)** | http://localhost:8080 |
| **Trang quản trị (Admin)** | http://localhost:8080/admin/dashboard |
| **Đăng ký tài khoản** | http://localhost:8080/register |
| **Đăng nhập** | http://localhost:8080/login |

---

## 🔐 Tài khoản và dữ liệu database

Ứng dụng mặc định chạy profile `local` khi chạy trên máy cá nhân. Profile này tạo schema, vai trò, sản phẩm demo, chi nhánh, hai kho demo và dữ liệu luyện tập Admin gồm đơn mẫu, nhân viên mẫu cùng một chi nhánh có cảnh báo tồn thấp. Bộ dữ liệu được bổ sung theo cách idempotent, không ghi đè tồn kho đã có. Dữ liệu luyện tập Admin chỉ chạy trong profile `local`; profile `prod` đã tắt chức năng này. Các tài khoản đã đăng ký, mật khẩu đã mã hóa, đơn hàng và thay đổi quản trị được lưu trong volume PostgreSQL trên từng máy; chúng không tự đi theo Git khi clone hoặc pull.

Để tạo admin mới trên máy local, cấu hình `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL` và `BOOTSTRAP_ADMIN_PASSWORD` trong biến môi trường của cấu hình Run trong IntelliJ rồi khởi động ứng dụng. Mật khẩu phải dài ít nhất 12 ký tự. Không lưu mật khẩu admin vào GitHub. Khách hàng có thể đăng ký tại `/register`. Admin vào **Người dùng → Thêm nhân viên** để tạo tài khoản nhân viên cửa hàng hoặc nhân viên kho, chọn địa điểm đang hoạt động và đặt mật khẩu khởi tạo tối thiểu 12 ký tự. Mật khẩu được mã hóa BCrypt; không gửi thông tin đăng nhập qua kênh công khai.

Để đăng nhập thử hai tài khoản nhân viên mẫu (`demo.branch.staff`, `demo.warehouse.staff`), đặt biến môi trường `DEMO_ADMIN_DATA_ACCOUNT_PASSWORD` có độ dài 12–72 byte UTF-8 trước khi chạy ứng dụng local. Nếu không đặt biến này, các tài khoản mẫu được tạo ở trạng thái khóa với mật khẩu ngẫu nhiên; Admin vẫn có thể dùng chúng để luyện xem, sửa và mở/khóa tài khoản. Đơn mẫu dùng thông tin khách và địa chỉ giả lập, không đại diện cho giao dịch thật. Khi chạy demo, Admin có thể vào **Trung tâm vận hành** để duyệt điều chuyển từ kho sang chi nhánh luyện tập.

Ở **Chi nhánh** và **Kho**, Admin chỉ cần gõ địa chỉ, chọn gợi ý để bản đồ tự ghim, rồi kéo ghim để căn đúng vị trí. Tọa độ được lưu tự động ở trường ẩn; không cần nhập vĩ độ/kinh độ.

### Sao chép nguyên dữ liệu sang máy khác

Nếu muốn máy mới có cùng tài khoản, mật khẩu hoạt động, đơn hàng, tồn kho và các chỉnh sửa như máy hiện tại, cần chuyển một bản sao PostgreSQL riêng. Dump chứa dữ liệu tài khoản, hash mật khẩu và thông tin đơn hàng/khách hàng, vì vậy không đưa file dump lên repo GitHub. Script sao lưu nằm trong `scripts/backup-local-db.ps1`, file đầu ra nằm trong `local-backups/` và đã được Git bỏ qua.

Máy hiện tại:

```powershell
docker compose up -d postgres
.\scripts\backup-local-db.ps1
```

Chép file `.sql` vừa tạo qua USB hoặc kho lưu trữ riêng tư có kiểm soát quyền truy cập. Máy mới cần Docker Desktop và source mới nhất:

```powershell
git clone https://github.com/KhoiLene/DOANWEB_Nhom-25_ST3_ST5_A3-204.git
cd DOANWEB_Nhom-25_ST3_ST5_A3-204
docker compose up -d postgres
```

Đặt file dump vào thư mục `local-backups` (tạo thư mục nếu cần), sau đó khôi phục **trước khi chạy ứng dụng lần đầu**:

```powershell
.\scripts\restore-local-db.ps1 -BackupPath .\local-backups\shop_chain_db_YYYYMMDD_HHMMSS.sql
```

Script hỏi xác nhận vì sẽ thay thế database `shop_chain_db` trên máy đích. Sau đó chạy project như bình thường. Database là ảnh chụp tại thời điểm sao lưu; thao tác phát sinh sau đó trên hai máy sẽ không tự đồng bộ. Qdrant không nằm trong SQL dump; dữ liệu tri thức dạng tài liệu nằm trong repo và được ứng dụng nạp lại khi khởi động.

---

## ⚙️ Cấu hình tích hợp

Không ghi khóa bí mật vào `application.properties`, Git, HTML hoặc JavaScript. Khi chạy IntelliJ, thêm giá trị vào **Run > Edit Configurations > Environment variables**; với Docker/hosting, dùng secrets/environment variables của môi trường.

| Biến | Dùng cho | Bắt buộc khi |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL | Luôn luôn |
| `JWT_SECRET` | Ký JWT ổn định; tối thiểu 32 byte | Môi trường dùng lâu dài/production |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Tạo admin lần đầu (mật khẩu tối thiểu 12 ký tự) | Chỉ lần khởi tạo khi chưa có tài khoản đó |
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | VNPay | Khi bật thanh toán VNPay |
| `VNPAY_URL`, `VNPAY_RETURN_URL` | Cổng thanh toán và trang kết quả quay về | Có thể để mặc định sandbox/local khi phát triển |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Gửi OTP qua SMTP/Gmail App Password | Khi đăng ký hoặc đặt lại mật khẩu cần OTP |
| `GEMINI_API_KEY` | Chat và embedding Gemini | Khi bật chatbot RAG |
| `QDRANT_HOST`, `QDRANT_GRPC_PORT`, `QDRANT_API_KEY`, `QDRANT_USE_TLS` | Vector store Qdrant | Khi bật chatbot RAG |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Lưu ảnh Cloudinary | Khi dùng upload ảnh |

VNPay và SMTP hỗ trợ cấu hình trong DB tại **Cài đặt hệ thống**. Quy tắc ưu tiên cho từng trường là **giá trị DB không trống > biến môi trường > mặc định không bí mật**. VNPay tạo URL và kiểm tra callback dùng cùng một cấu hình hiệu lực. SMTP tạo sender theo cấu hình hiệu lực cho từng OTP; thay đổi ở trang admin có hiệu lực ở lần gửi tiếp theo. Thay đổi biến môi trường cần khởi động lại ứng dụng. Secret được lưu trống khi lưu form sẽ giữ nguyên giá trị cũ; trang không trả secret về trình duyệt.

Trong bảng điều khiển merchant VNPay, cấu hình IPN URL thành `https://<domain>/api/payment/vnpay-ipn` (HTTPS bắt buộc khi public). Return URL chỉ hiển thị kết quả cho khách; IPN server-to-server mới cập nhật thanh toán nếu khách đóng trình duyệt hoặc không quay về. Bản tích hợp chưa có truy vấn giao dịch/hoàn tiền tự động; giao dịch không thể cập nhật sẽ được VNPAY thử gửi lại và cần đối soát nếu vẫn lỗi. Xem [tài liệu chính thức VNPay PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Gmail và Zalo cho thông báo đơn hàng ngoài OTP hiện **chưa được hỗ trợ**. Kênh thông báo nội bộ trên web vẫn dùng WebSocket. Chatbot hiện là **Vector RAG theo role**: PostgreSQL giữ tài liệu, Gemini tạo câu trả lời/embedding và Qdrant tìm vector; hệ thống chưa truy hồi quan hệ đồ thị nên không gọi là GraphRAG. Bản đồ dùng Leaflet/OpenStreetMap. Xem [`docs/integrations-setup.md`](docs/integrations-setup.md), [`docs/GMAIL_OTP_SETUP.md`](docs/GMAIL_OTP_SETUP.md) và [`docs/deploy-free-host.md`](docs/deploy-free-host.md).

---

## 🛠 Một số lệnh Docker hữu ích

```bash
# Kiểm tra container đang chạy
docker compose ps

# Xem log của PostgreSQL
docker logs shop-chain-db

# Mở psql để xem/sửa dữ liệu trực tiếp
docker exec -it shop-chain-db psql -U postgres -d shop_chain_db

# Dừng container (giữ nguyên data)
docker compose stop

# Khởi động lại container sau khi stop
docker compose start
```

---

## ❗ Xử lý lỗi thường gặp

### Lỗi: `external volume "web_db_data" not found`
**Nguyên nhân:** Container cũ còn sót lại.
**Cách fix:**
```bash
docker compose down        # Dừng và xóa container (KHÔNG xóa data)
.\mvnw clean spring-boot:run
```

### Lỗi: `No host port mapping found for container port 5432`
**Nguyên nhân:** Container đang chạy nhưng không có port mapping.
**Cách fix:**
```bash
docker compose up -d --force-recreate
.\mvnw clean spring-boot:run
```

### Lỗi: `UnsupportedClassVersionError` hoặc `release version XX not supported`
**Nguyên nhân:** Java trên máy không khớp với phiên bản trong `pom.xml`.
**Cách fix:** Mở `pom.xml`, tìm dòng `<java.version>` và đổi về đúng phiên bản Java đang cài trên máy (17 hoặc 21).
```xml
<java.version>17</java.version>
```
Sau đó chạy lại: `.\mvnw clean spring-boot:run`

### Lỗi: `Connection refused` khi kết nối database
**Nguyên nhân:** Docker Desktop chưa mở hoặc container chưa chạy.
**Cách fix:** Mở Docker Desktop, chờ engine khởi động xong rồi chạy lại project.

---

## 📁 Cấu trúc thư mục chính

```
├── src/main/java/com/shopchain/
│   ├── controller/          # Web MVC + REST API controllers
│   │   └── admin/           # Trang quản trị
│   ├── entity/              # JPA Entities (20 bảng)
│   ├── service/             # Business logic
│   ├── repository/          # Truy vấn database
│   ├── config/              # Security, WebSocket, Cloudinary
│   └── security/            # JWT Filter, Token Provider
│
├── src/main/resources/
│   ├── templates/           # Giao diện Thymeleaf (29 files)
│   │   └── admin/           # Giao diện Admin
│   ├── static/              # CSS, JS, ảnh tĩnh
│   └── application.properties
│
├── compose.yaml             # Docker Compose cho development (Spring Boot tự dùng)
├── docker-compose.yml       # Docker Compose cho production (có thêm Redis)
├── init.sql                 # SQL schema tham khảo, RLS mặc định chặn truy cập trực tiếp
└── pom.xml
```

> Với cơ sở dữ liệu tham khảo trong `init.sql`, vai trò `anon` và `authenticated` chỉ đọc các view `v_public_*`; backend cần dùng `service_role`. Không cấp quyền ghi trực tiếp cho trình duyệt.

---

## 🧰 Tech Stack

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17+, Spring Boot 4.1.2 |
| Frontend | Thymeleaf (SSR), Bootstrap 5, Vanilla JS |
| Database | PostgreSQL 16 |
| Auth | JWT (Stateless) + BCrypt |
| Payment | VNPay |
| Storage | Cloudinary |
| Realtime | WebSocket STOMP |
| Email | Spring Mail (OTP) |
| Container | Docker Compose |
