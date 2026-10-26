# Quản lý nguồn Gemini RAG

Ứng dụng nạp các tài liệu mẫu theo vai trò từ `src/main/resources/assistant-knowledge/` vào PostgreSQL. Cẩm nang chuyên đề cho khách hàng gồm kiến thức làm đẹp cơ bản, danh mục sản phẩm, chăm sóc tóc và chăm sóc môi. Admin quản lý nguồn tại `/admin/assistant-knowledge`.

Mỗi nguồn gắn với một role duy nhất: `customer`, `store-staff`, `warehouse` hoặc `admin`. Backend lấy role từ phiên đăng nhập, lọc vector đúng role trong Qdrant và xác nhận lại nguồn còn hoạt động trong PostgreSQL trước khi gửi đoạn nội dung tới Gemini.

Trang admin hiển thị trạng thái index và quả cầu láng giềng embedding thật. Nguồn mới hoặc sửa sẽ được lập chỉ mục nền bằng Gemini Embedding 2; các nguồn mẫu mới được thêm khi khởi động sẽ lưu vào PostgreSQL với trạng thái `PENDING`. Nếu thiếu `GEMINI_API_KEY` hoặc Qdrant, dữ liệu vẫn được giữ trong PostgreSQL. Sau khi cấu hình key và chạy Qdrant, mở `/admin/assistant-knowledge`, nhấn **Lập chỉ mục tất cả** để đưa nguồn đang chờ vào RAG.

Khởi động nhanh:

```powershell
$env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
docker compose up -d qdrant
.\mvnw.cmd spring-boot:run
```

Gemini Free Tier có thể dùng nội dung gửi lên để cải thiện sản phẩm. Chỉ đưa tài liệu công khai/đã duyệt và đã làm sạch vào RAG; không đưa dữ liệu cá nhân, đơn hàng, địa chỉ khách hoặc bí mật kinh doanh. Xem [hướng dẫn Gemini RAG](../integrations-setup.md).
