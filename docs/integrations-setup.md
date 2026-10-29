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

Mặc định dùng `gemini-3.5-flash-lite` cho câu trả lời, `gemini-embedding-2` với vector 768 chiều cho truy xuất, và collection `techtra_knowledge_gemini_embedding_2_768`. Đây là Vector RAG: truy xuất theo độ gần embedding và lọc role, chưa dùng truy hồi quan hệ đồ thị nên chưa phải GraphRAG. Có thể đổi qua `ASSISTANT_CHAT_MODEL`, `ASSISTANT_EMBEDDING_MODEL`, `ASSISTANT_EMBEDDING_DIMENSIONS`, `ASSISTANT_QDRANT_COLLECTION`, `QDRANT_HOST` và `QDRANT_GRPC_PORT`. Khi đổi embedding model hoặc dimension, dùng collection mới rồi index lại tất cả nguồn.

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

## VNPay và SMTP

### Thứ tự cấu hình hiệu lực

- Mỗi khóa VNPay/SMTP dùng giá trị DB không trống trước, rồi fallback sang biến môi trường tương ứng. Mặc định URL VNPay là sandbox; mã merchant/secret và thông tin gửi SMTP không có giá trị mặc định.
- Trang **Admin > Cài đặt hệ thống** hiển thị cấu hình đang hoạt động (không hiển thị secret). Ô secret luôn để trống; gửi form với ô secret trống giữ nguyên giá trị đã lưu.
- SMTP được dựng theo cấu hình hiệu lực cho mỗi lần gửi OTP. Giá trị DB đổi có hiệu lực ngay; đổi biến môi trường cần restart ứng dụng.
- SMTP chỉ dùng cho OTP đăng ký và khôi phục mật khẩu. Gmail thông báo đơn hàng và Zalo OA hiện chưa được cài đặt; việc lưu credentials Zalo cũ không kích hoạt việc gửi.

### VNPay server callback

Thiết lập `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, tùy chọn `VNPAY_URL` và `VNPAY_RETURN_URL`. Có thể nhập mã merchant và hash secret trong trang cài đặt; giá trị DB không trống sẽ ưu tiên biến môi trường. Cùng cấu hình hiệu lực được dùng để tạo chữ ký URL thanh toán và xác minh chữ ký callback.

Trong merchant portal VNPay, cấu hình IPN URL `https://<domain>/api/payment/vnpay-ipn`. Endpoint nhận GET server-to-server, kiểm tra chữ ký, mã merchant, mã đơn và số tiền; sau đó cập nhật đơn, điểm và biên nhận theo luồng idempotent. Trang `/api/payment/vnpay-return` hiển thị kết quả cho trình duyệt và vẫn xử lý callback hợp lệ như một đường dự phòng. Nếu khách không quay về, IPN cập nhật giao dịch. Giao dịch đến sau khi đơn đã hết hạn/không thể xác nhận sẽ được trả lỗi xử lý để VNPay thử lại; project chưa có truy vấn trạng thái hoặc hoàn tiền tự động, nên cần đối soát thủ công nếu retry vẫn thất bại.

Mã phản hồi IPN dùng định nghĩa PAY của VNPay: `00` xử lý thành công, `02` đã xác nhận, `01` không tìm thấy đơn, `04` sai số tiền, `97` sai chữ ký và `99` lỗi xử lý/đối soát. Tham khảo [tài liệu tích hợp PAY chính thức](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), nơi mô tả riêng Return URL cho trình duyệt và IPN URL để merchant cập nhật trạng thái từ server.

### Bật môi trường local

Thêm vào IntelliJ Run Configuration hoặc PowerShell, không commit giá trị thật:

```powershell
$env:VNPAY_TMN_CODE = "YOUR_TMN_CODE"
$env:VNPAY_HASH_SECRET = "YOUR_HASH_SECRET"
$env:VNPAY_RETURN_URL = "http://localhost:8080/api/payment/vnpay-return"
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "your-address@gmail.com"
$env:MAIL_PASSWORD = "your-google-app-password"
```

Chỉ chạy kiểm thử dùng mock/stub; không gửi email hoặc thanh toán thật. Việc IPN public cần domain HTTPS và cấu hình URL trong merchant portal; chưa xác minh đầu cuối nếu không có merchant sandbox credentials và endpoint public.
