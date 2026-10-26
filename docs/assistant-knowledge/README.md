# Quản lý nguồn RAG

Ứng dụng nạp bốn tài liệu mặc định từ `src/main/resources/assistant-knowledge/` vào PostgreSQL khi khởi tạo database. Sau đó admin quản lý nội dung đang dùng tại `/admin/assistant-knowledge`.

Trang quản lý có đồ thị quả cầu tương tác thể hiện liên kết giữa lõi RAG, bốn role, tài liệu và các khái niệm trích ra từ nội dung. Chọn nút role để lọc danh sách; chọn nút tài liệu để mở nguồn trong trình sửa.

Mỗi nguồn được gán một phạm vi role duy nhất. Backend lấy role từ phiên đăng nhập và quyền đã xác thực, chỉ truy xuất tài liệu role đó, và chỉ đưa các đoạn truy xuất vào Ollama local. Nếu không có Ollama, chatbot trả trích đoạn trực tiếp. Không thêm khóa bí mật, token hoặc dữ liệu cá nhân vào nguồn tri thức.
