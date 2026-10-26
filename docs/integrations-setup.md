# Bản đồ miễn phí và chatbot Gemini RAG

## Bản đồ chi nhánh

Project dùng Leaflet và tile OpenStreetMap, không cần Google Maps API key hoặc tài khoản billing. Tìm địa chỉ gọi Nominatim qua backend, sau đó thử từ trình duyệt nếu backend không truy cập được dịch vụ công cộng. Trang `/stores` và checkout chỉ tra địa chỉ sau khi khách bấm tìm. Admin có thể định vị địa chỉ ở Việt Nam hoặc kéo ghim trên bản đồ.

GPS dùng `navigator.geolocation`, chỉ được hỏi quyền sau khi người dùng nhấn nút. Vị trí GPS không được lưu vào database. API nội bộ tính khoảng cách và giới hạn tối đa 5 gợi ý.

Môi trường demo tự thêm 9 điểm thử nghiệm tại các thành phố lớn và tồn kho mẫu cho sản phẩm demo. Tên và địa chỉ ghi rõ là mô phỏng; thay bằng chi nhánh thật và tồn kho đã xác nhận trước khi đưa dữ liệu lên môi trường vận hành.

Nominatim công cộng phù hợp cho demo/lưu lượng thấp: project giới hạn tối đa một request mỗi giây cho mỗi tiến trình backend và mỗi tab, không autocomplete và chỉ tra một địa chỉ mỗi lần. Dịch vụ có thể giới hạn traffic và không đảm bảo SLA. Khi vận hành thực tế, tự host geocoder/tiles hoặc chọn nhà cung cấp có SLA. Xem [Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/) và [OpenStreetMap Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).

## Chatbot Gemini RAG theo role

Luồng Gemini đã được nối vào ứng dụng. Chat và embedding đều gọi Gemini Developer API; không gọi Ollama, Qwen, BGE-M3 hoặc OpenAI. PostgreSQL là nguồn chuẩn, Qdrant local lưu vector.

### Chạy local

1. Tạo API key trong Google AI Studio. Không dán key vào chat, mã nguồn, HTML hoặc JavaScript.
2. Mở PowerShell tại thư mục project, đặt key trong terminal hiện tại rồi chạy Qdrant và ứng dụng:

   ```powershell
   $env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
   docker compose up -d qdrant
   .\mvnw.cmd spring-boot:run
   ```

   Với file JAR đã build, vẫn đặt biến môi trường trong cùng terminal trước khi chạy `java -jar target/*.jar`.
3. Mở `/admin/assistant-knowledge`. Nguồn cũ trong PostgreSQL sẽ được đưa vào hàng chờ index sau khi ứng dụng khởi động; nút **Lập chỉ mục tất cả** sẽ tạo lại vector khi cần.
4. Trạng thái nguồn chuyển từ `PENDING` sang `INDEXED`, `FAILED` hoặc `DISABLED`. Chat chỉ bật khi có Gemini key, Qdrant kết nối được và role có nguồn `INDEXED`.

Mặc định dùng `gemini-3.1-flash-lite` cho câu trả lời, `gemini-embedding-2` với vector 768 chiều cho truy xuất, và collection `techtra_knowledge_gemini_embedding_2_768`. Có thể đổi qua `ASSISTANT_CHAT_MODEL`, `ASSISTANT_EMBEDDING_MODEL`, `ASSISTANT_EMBEDDING_DIMENSIONS`, `ASSISTANT_QDRANT_COLLECTION`, `QDRANT_HOST` và `QDRANT_GRPC_PORT`. Khi đổi embedding model hoặc dimension, dùng collection mới rồi index lại tất cả nguồn.

Nếu chưa đặt `GEMINI_API_KEY`, website vẫn khởi động nhưng chatbot hiển thị trạng thái chưa cấu hình. API key chỉ được đọc ở backend và không được log. Qdrant chỉ bind vào loopback `127.0.0.1` trong Compose.

### Phân quyền và dữ liệu

- Role lấy từ phiên đăng nhập ở backend. Truy vấn Qdrant lọc `roleKey` và `active=true`, sau đó backend xác nhận lại nguồn trong PostgreSQL.
- Chat chỉ gửi các đoạn đã truy xuất đúng role tới Gemini. Câu trả lời không được quyền tạo đơn, sửa tồn kho, phát hành hóa đơn hoặc đổi quyền.
- Email và số điện thoại được che trước khi gửi. Tuy nhiên không đưa dữ liệu cá nhân, dữ liệu đơn hàng, địa chỉ khách hoặc bí mật kinh doanh vào nguồn tri thức.
- Gemini Free Tier có thể sử dụng nội dung để cải thiện sản phẩm. Chỉ lập chỉ mục tài liệu công khai/đã duyệt và đã làm sạch. Xem điều khoản hiện hành tại [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing) và giới hạn tại [Gemini rate limits](https://ai.google.dev/gemini-api/docs/rate-limits).
- Quả cầu admin hiển thị láng giềng embedding thật cùng role. Đường nối chỉ thể hiện độ gần vector, không phải quan hệ chuyên môn đã được xác minh.

### Kiến trúc và tham chiếu

Spring AI 2.0.2 cung cấp abstraction vector store cho Qdrant; adapter embedding trong project gọi Gemini REST trực tiếp để hỗ trợ `gemini-embedding-2`. Chat cũng gọi Gemini REST trực tiếp. Điều này giúp không tạo đường fallback tới model khác và giữ ứng dụng chạy được khi thiếu key.

Xem thêm: [Gemini Embedding 2](https://ai.google.dev/gemini-api/docs/embeddings), [Gemini API embeddings](https://ai.google.dev/api/embeddings), [Spring AI Qdrant](https://docs.spring.io/spring-ai/reference/2.1/api/vectordbs/qdrant.html), [Spring AI và Spring Boot](https://docs.spring.io/spring-ai/reference/2.1/getting-started.html), [kế hoạch và chi tiết triển khai](graph-rag-integration-plan.md).
