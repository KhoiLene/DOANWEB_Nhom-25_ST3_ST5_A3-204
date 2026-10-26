# Cấu hình bản đồ và chatbot

Các tích hợp tắt an toàn khi thiếu cấu hình. Không commit giá trị thật vào `application.properties` hoặc Git.

## Thiết lập trên Windows PowerShell

Mở PowerShell tại thư mục gốc của repo. Các lệnh `$env:...` chỉ tồn tại trong cửa sổ PowerShell hiện tại; chúng không ghi khóa vào Git. Không dán khóa vào chat hoặc ảnh chụp màn hình.

```powershell
# Sau khi tạo key trên Google Cloud Console:
$env:GOOGLE_MAPS_BROWSER_KEY = "YOUR_RESTRICTED_BROWSER_KEY"

# Sau khi tạo OpenAI API key:
$env:OPENAI_API_KEY = "YOUR_OPENAI_API_KEY"
python .\scripts\provision_assistant_vector_stores.py
```

Lệnh provisioning in ra bốn vector store ID. Chờ trạng thái từng file là `completed` trong OpenAI dashboard, rồi đặt các ID được in ra vào cùng cửa sổ PowerShell:

```powershell
$env:OPENAI_VECTOR_STORE_CUSTOMER = "vs_customer_id"
$env:OPENAI_VECTOR_STORE_STORE_STAFF = "vs_store_staff_id"
$env:OPENAI_VECTOR_STORE_WAREHOUSE = "vs_warehouse_id"
$env:OPENAI_VECTOR_STORE_ADMIN = "vs_admin_id"
$env:OPENAI_MODEL = "gpt-4.1-mini"
```

Sau đó khởi động ứng dụng trong cửa sổ đó để Spring nhận các biến:

```powershell
.\mvnw.cmd spring-boot:run
```

Nếu chạy app bằng Docker hoặc IDE, hãy khai báo các biến ở cấu hình môi trường của chính tiến trình Spring. Đừng đặt `OPENAI_API_KEY` vào JavaScript, HTML, `application.properties` hay Docker image. Key Maps được trình duyệt nhìn thấy theo thiết kế; giới hạn bằng website referrer và chỉ bật APIs cần dùng.

## Google Maps

1. Tạo browser key trong Google Cloud, bật Maps JavaScript API và Geocoding API.
2. Giới hạn key theo HTTP referrer cho môi trường phát triển và domain website khi triển khai; chỉ cho phép các API cần thiết.
3. Đặt biến môi trường `GOOGLE_MAPS_BROWSER_KEY` trước khi khởi động Spring như phần PowerShell ở trên.
4. Tại `/admin/locations/stores`, nhập địa chỉ rồi định vị hoặc đặt ghim lên bản đồ; kiểm tra tọa độ trước khi lưu.
5. Tại `/stores`, khách nhập địa chỉ hoặc bấm nút GPS. Website geocode địa chỉ bằng Maps JavaScript API rồi gọi API nội bộ để trả tối đa 5 chi nhánh gần nhất.

Nếu chưa có khóa, nhập tọa độ thủ công ở admin và tìm khu vực theo tên/địa chỉ chi nhánh vẫn hoạt động. Không lưu vị trí GPS của khách vào cơ sở dữ liệu.

## OpenAI RAG

1. Rà soát tài liệu từng role trong `docs/assistant-knowledge/`, sau đó chạy `python scripts/provision_assistant_vector_stores.py` với `OPENAI_API_KEY` đã đặt ở môi trường cục bộ. Script tạo bốn vector store, tải mỗi tài liệu vào store riêng và in các ID.
2. Cấu hình `OPENAI_API_KEY`, `OPENAI_MODEL` và lần lượt `OPENAI_VECTOR_STORE_CUSTOMER`, `OPENAI_VECTOR_STORE_STORE_STAFF`, `OPENAI_VECTOR_STORE_WAREHOUSE`, `OPENAI_VECTOR_STORE_ADMIN`.
3. Khởi động lại Spring. Chatbot trên storefront, admin, kho và POS sẽ lấy role từ JWT, chỉ tìm trong vector store đã gán cho role đó.
4. Nếu một biến còn thiếu, chatbot sẽ hiện trạng thái chưa sẵn sàng và API không gửi câu hỏi sang nhà cung cấp.

Mọi câu hỏi chatbot được gửi tới OpenAI Responses API khi tích hợp đã bật. API key chỉ nằm ở backend. Chatbot hiện chỉ trả lời dựa trên tài liệu; nó không đọc tồn kho/đơn trực tiếp và không thực hiện thao tác ghi dữ liệu.
