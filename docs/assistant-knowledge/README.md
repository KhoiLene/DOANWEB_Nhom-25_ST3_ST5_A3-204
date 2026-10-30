# Quản lý nguồn Gemini RAG

Ứng dụng nạp các tài liệu mẫu theo vai trò từ `src/main/resources/assistant-knowledge/` vào PostgreSQL. Cẩm nang khách hàng gồm kiến thức làm đẹp, sản phẩm, chăm sóc tóc/môi, tìm chi nhánh, đặt hàng, VietQR, ưu đãi và điểm. Tài liệu cửa hàng/kho/admin mô tả đúng màn hình và quy trình hiện có. Admin quản lý nguồn tại `/admin/assistant-knowledge`.

Mỗi nguồn gắn với một role duy nhất: `customer`, `store-staff`, `warehouse` hoặc `admin`. Backend lấy role từ phiên đăng nhập, lọc vector đúng role trong Qdrant và xác nhận lại nguồn còn hoạt động trong PostgreSQL trước khi gửi đoạn nội dung tới Gemini.

Trang admin hiển thị trạng thái index và quả cầu láng giềng embedding thật; đây là Vector RAG, không phải GraphRAG. Nguồn mới hoặc sửa sẽ được lập chỉ mục nền bằng Gemini Embedding 2. Khi phát hành phiên bản mới có thay đổi tài liệu mặc định, ứng dụng đồng bộ các nguồn mặc định đã được nạp trước đó đúng một lần cho revision đó, đặt lại trạng thái index và yêu cầu lập chỉ mục lại. Sửa tay nguồn mặc định trong admin không bị ghi đè ở mỗi lần khởi động; một revision tài liệu mới chỉ được áp dụng khi đổi mã revision.

Nếu thiếu `GEMINI_API_KEY` hoặc Qdrant, nội dung vẫn được lưu trong PostgreSQL nhưng chatbot chưa thể truy hồi bằng vector. Sau khi cấu hình key và chạy Qdrant, mở `/admin/assistant-knowledge`, kiểm tra đủ bốn role, sau đó nhấn **Lập chỉ mục tất cả** nếu nguồn còn `PENDING`.

Tài liệu mặc định không chứa dữ liệu cá nhân hay bí mật. Khi cập nhật hướng dẫn, kiểm tra luồng thực tế trước, giữ nội dung đúng role, tránh hướng dẫn chatbot tự xác nhận thanh toán hoặc thay đổi đơn/tồn. VietQR không phải đối soát ngân hàng tự động; nhân viên phải xác minh giao dịch thật. Khách đổi điểm theo quy tắc hiện tại: 1 điểm = 100 đồng.

Khởi động nhanh:

```powershell
$env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
docker compose up -d qdrant
.\mvnw.cmd spring-boot:run
```

Gemini Free Tier có thể dùng nội dung gửi lên để cải thiện sản phẩm. Chỉ đưa tài liệu công khai/đã duyệt và đã làm sạch vào RAG; không đưa dữ liệu cá nhân, đơn hàng, địa chỉ khách hoặc bí mật kinh doanh. Xem [hướng dẫn Gemini RAG](../integrations-setup.md).
