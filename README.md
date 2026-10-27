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

## 🔐 Tài khoản Admin

Database được tạo trống khi chạy lần đầu. Để có tài khoản Admin, làm một trong hai cách sau:

**Cách 1 — Tạo tài khoản User rồi nâng cấp lên Admin qua SQL:**
```bash
# Mở terminal trong container PostgreSQL
docker exec -it shop-chain-db psql -U postgres -d shop_chain_db

# Sau đó chạy lệnh SQL này để tạo Role và User admin:
INSERT INTO roles (name) VALUES ('ROLE_ADMIN'), ('ROLE_USER') ON CONFLICT DO NOTHING;

INSERT INTO users (username, email, password_hash, full_name, is_active, role_id)
VALUES (
  'admin',
  'admin@techtra.vn',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVyc0PHMXW',
  'Admin',
  true,
  (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')
);
-- Mật khẩu mặc định ở trên là: password123
\q
```

**Cách 2 — Đăng ký tài khoản User bình thường:**
Vào http://localhost:8080/register, đăng ký tài khoản rồi dùng để test chức năng mua hàng (không có quyền Admin).

---

## ⚙️ Cấu hình tùy chọn

Nếu cần dùng các tính năng tích hợp bên ngoài, mở file `src/main/resources/application.properties` và điền thông tin:

```properties
# Upload ảnh sản phẩm (Cloudinary)
cloudinary.cloud-name=YOUR_CLOUD_NAME
cloudinary.api-key=YOUR_API_KEY
cloudinary.api-secret=YOUR_API_SECRET

# Thanh toán VNPay
vnpay.tmn_code=YOUR_TMN_CODE
vnpay.hash_secret=YOUR_HASH_SECRET

# Gửi Email OTP (Gmail / SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

> Nếu không điền, các tính năng trên sẽ không hoạt động nhưng **project vẫn chạy bình thường**.

Bản đồ chi nhánh dùng Leaflet/OpenStreetMap, chatbot dùng RAG cục bộ trong PostgreSQL; không cần thẻ thanh toán hay API key. Hướng dẫn bật Ollama tùy chọn và các giới hạn dịch vụ bản đồ công cộng nằm tại [`docs/integrations-setup.md`](docs/integrations-setup.md).

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
├── init.sql                 # SQL schema tham khảo
└── pom.xml
```

---

## 🧰 Tech Stack

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17+, Spring Boot 4.1.1 |
| Frontend | Thymeleaf (SSR), Bootstrap 5, Vanilla JS |
| Database | PostgreSQL 16 |
| Auth | JWT (Stateless) + BCrypt |
| Payment | VNPay |
| Storage | Cloudinary |
| Realtime | WebSocket STOMP |
| Email | Spring Mail (OTP) |
| Container | Docker Compose |
