# Bản đồ miễn phí và chatbot RAG cục bộ

## Bản đồ chi nhánh

Project không cần Google Maps API key hoặc tài khoản billing. Bản đồ dùng Leaflet để hiển thị tile OpenStreetMap; tìm địa chỉ gọi Nominatim qua backend, sau đó thử trực tiếp từ trình duyệt nếu backend không truy cập được dịch vụ công cộng. Trang `/stores` và checkout chỉ gửi địa chỉ đi tra cứu sau khi khách bấm tìm. Admin chọn tọa độ chi nhánh bằng cách định vị địa chỉ trong Việt Nam hoặc kéo ghim trên bản đồ. Nếu cả hai đường geocoding đều không khả dụng, khách vẫn có thể tìm chi nhánh theo tên/tỉnh/thành đã lưu trong database.

GPS dùng `navigator.geolocation`, chỉ được hỏi quyền sau khi người dùng nhấn nút. Vị trí GPS không được lưu vào database. Kết quả chi nhánh vẫn do API nội bộ tính khoảng cách và giới hạn tối đa 5 gợi ý.

Môi trường demo tự thêm 9 điểm thử nghiệm ở các thành phố lớn và tồn kho mẫu cho các sản phẩm demo. Tên và địa chỉ đều ghi rõ là vị trí mô phỏng; cần thay bằng chi nhánh thật và tồn kho đã xác nhận trước khi đưa dữ liệu này lên môi trường vận hành.

Nominatim công cộng là dịch vụ cộng đồng: ứng dụng giới hạn tối đa một request mỗi giây cho mỗi tiến trình backend và cho mỗi tab trình duyệt, không autocomplete và chỉ tra một địa chỉ mỗi lần. Dịch vụ có thể giới hạn hoặc từ chối traffic; phù hợp cho demo/lưu lượng thấp. OpenStreetMap tile server cũng có chính sách sử dụng và không đảm bảo SLA. Khi vận hành sản phẩm thật với nhiều người dùng, nên tự host geocoder/tiles hoặc chọn nhà cung cấp có SLA. Xem [Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/) và [OpenStreetMap Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).

## Chatbot role scoped RAG

RAG chạy miễn phí trong ứng dụng:

- Bốn kho tri thức riêng cho `customer`, `store-staff`, `warehouse` và `admin` được lưu trong PostgreSQL.
- Khi khởi động lần đầu, ứng dụng nạp tài liệu mẫu trong `src/main/resources/assistant-knowledge/`.
- Admin tạo, nhập tệp `.md`/`.txt`, chỉnh sửa, bật/tắt hoặc xóa nguồn tại `/admin/assistant-knowledge`.
- Backend xác định role từ phiên đăng nhập và quyền đã xác thực, rồi truy xuất đúng nguồn đang bật cho role đó. Role không được nhận từ payload trình duyệt.
- Nếu Ollama chưa chạy, bot vẫn phản hồi bằng các trích đoạn khớp từ tài liệu role đó. Khi Ollama cài mô hình, bot tổng hợp câu trả lời từ chính những đoạn đã truy xuất.
- Nội dung câu hỏi và tài liệu RAG không gửi tới OpenAI hay dịch vụ AI bên ngoài.

### Không cần cài thêm gì để dùng fallback RAG

Chạy app như hiện tại. Sau khi đăng nhập bằng một trong bốn role, widget trợ lý sẽ xuất hiện. Khi chưa có nguồn tri thức hoạt động cho role, admin thêm hoặc bật nguồn trong trang quản lý RAG.

### Bật câu trả lời sinh bởi mô hình local (tùy chọn)

1. Cài Ollama từ [ollama.com/download](https://ollama.com/download).
2. Mở PowerShell và tải mô hình nhỏ đa ngôn ngữ:

   ```powershell
   ollama pull qwen2.5:1.5b
   ```

3. Đảm bảo Ollama đang chạy ở `http://localhost:11434`, rồi khởi động lại Spring. Đây là địa chỉ và endpoint mặc định Ollama.

Project mặc định dùng `qwen2.5:1.5b`. Có thể đổi `OLLAMA_BASE_URL` hoặc `OLLAMA_MODEL` trong môi trường tiến trình Spring. Mô hình Qwen2.5 1.5B khoảng 986 MB và hỗ trợ tiếng Việt; model page liệt kê các biến thể và giấy phép của dòng model. [Ollama Chat API](https://docs.ollama.com/api/chat) · [Qwen2.5 model library](https://ollama.com/library/qwen2.5).

Ollama chạy trên máy của bạn; tốc độ và lượng RAM phụ thuộc cấu hình máy. Nếu Ollama không có hoặc chưa tải model, backend tự dùng phản hồi trích đoạn để chatbot không bị tắt.
