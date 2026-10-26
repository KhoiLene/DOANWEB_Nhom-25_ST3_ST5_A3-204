# Bản đồ miễn phí và kế hoạch chatbot Gemini RAG

## Bản đồ chi nhánh

Project không cần Google Maps API key hoặc tài khoản billing. Bản đồ dùng Leaflet để hiển thị tile OpenStreetMap; tìm địa chỉ gọi Nominatim qua backend, sau đó thử trực tiếp từ trình duyệt nếu backend không truy cập được dịch vụ công cộng. Trang `/stores` và checkout chỉ gửi địa chỉ đi tra cứu sau khi khách bấm tìm. Admin chọn tọa độ chi nhánh bằng cách định vị địa chỉ trong Việt Nam hoặc kéo ghim trên bản đồ. Nếu cả hai đường geocoding đều không khả dụng, khách vẫn có thể tìm chi nhánh theo tên/tỉnh/thành đã lưu trong database.

GPS dùng `navigator.geolocation`, chỉ được hỏi quyền sau khi người dùng nhấn nút. Vị trí GPS không được lưu vào database. Kết quả chi nhánh vẫn do API nội bộ tính khoảng cách và giới hạn tối đa 5 gợi ý.

Môi trường demo tự thêm 9 điểm thử nghiệm ở các thành phố lớn và tồn kho mẫu cho các sản phẩm demo. Tên và địa chỉ đều ghi rõ là vị trí mô phỏng; cần thay bằng chi nhánh thật và tồn kho đã xác nhận trước khi đưa dữ liệu này lên môi trường vận hành.

Nominatim công cộng là dịch vụ cộng đồng: ứng dụng giới hạn tối đa một request mỗi giây cho mỗi tiến trình backend và cho mỗi tab trình duyệt, không autocomplete và chỉ tra một địa chỉ mỗi lần. Dịch vụ có thể giới hạn hoặc từ chối traffic; phù hợp cho demo/lưu lượng thấp. OpenStreetMap tile server cũng có chính sách sử dụng và không đảm bảo SLA. Khi vận hành sản phẩm thật với nhiều người dùng, nên tự host geocoder/tiles hoặc chọn nhà cung cấp có SLA. Xem [Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/) và [OpenStreetMap Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).

## Chatbot Gemini RAG theo role

Phần này là kế hoạch chuyển đổi, chưa phải hướng dẫn bật Gemini trong code hiện tại. Mục tiêu là dùng Gemini API cho cả chat và embedding; không tiếp tục hướng Ollama/Qwen/BGE-M3.

- PostgreSQL tiếp tục giữ nguồn tri thức cho `customer`, `store-staff`, `warehouse`, `admin`.
- Dự kiến dùng Gemini 3.1 Flash-Lite để sinh câu trả lời, Gemini Embedding 2 để vector hóa, Qdrant local để lưu/search vector và hỗ trợ quả cầu láng giềng embedding.
- Spring Security lấy role ở backend; Qdrant filter role và trạng thái active là bắt buộc, sau đó backend kiểm tra lại nguồn trong PostgreSQL.
- Google hiện ghi Gemini 3.1 Flash-Lite và Embedding 2 có Free Tier. Free Tier cũng ghi rằng nội dung được dùng để cải thiện sản phẩm; chỉ gửi tài liệu công khai/đã làm sạch, không gửi PII, dữ liệu đơn hàng hoặc bí mật chưa được duyệt. Quota miễn phí thay đổi theo project/model, cần kiểm tra trong AI Studio.
- API key sẽ được lưu ở biến môi trường backend `GEMINI_API_KEY`, không ghi trong mã nguồn hoặc trình duyệt. Khi Gemini thiếu key, lỗi hoặc hết quota, bot sẽ báo rõ và không gọi lại model cũ.

### Lộ trình

1. Xác nhận Spring AI tương thích Boot 4.1.1 và thử Gemini Embedding 2 với dimension cố định.
2. Thêm Gemini chat/embedding starters, Qdrant local và trạng thái index theo model/hash vào quản lý nguồn tri thức.
3. Index lại tài liệu PostgreSQL bằng Gemini Embedding 2; truy vấn vector giới hạn role và active.
4. Nối widget với Gemini 3.1 Flash-Lite, thêm nguồn dẫn, giới hạn ngữ cảnh và kiểm thử role/quota/privacy.
5. Hoàn thành kiểm thử rồi gỡ toàn bộ Ollama/Qwen/BGE-M3 và cấu hình liên quan khỏi ứng dụng.

Chi tiết kiến trúc, bảo mật, dữ liệu free tier, đồ thị embedding và tiêu chí cắt chuyển nằm ở [kế hoạch Gemini RAG](graph-rag-integration-plan.md). Xem thêm [Spring AI Google GenAI chat](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html), [Spring AI Google GenAI embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [Gemini Embedding 2](https://ai.google.dev/gemini-api/docs/embeddings) và [Gemini rate limits](https://ai.google.dev/gemini-api/docs/rate-limits).
