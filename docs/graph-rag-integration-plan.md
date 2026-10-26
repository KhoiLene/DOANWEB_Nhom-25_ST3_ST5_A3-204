# Kế hoạch Gemini RAG theo role và đồ thị embedding

> Trạng thái: tài liệu kế hoạch; Gemini chưa được nối vào ứng dụng. Kế hoạch này thay hẳn hướng Ollama/Qwen/BGE-M3 đã bàn trước đó.

## Mục tiêu và quyết định kiến trúc

- Dùng Gemini Developer API cho cả sinh câu trả lời và embedding; không gọi Ollama, không tải/chạy Qwen hoặc BGE-M3, không gửi dữ liệu qua OpenAI.
- Giữ PostgreSQL làm nguồn chuẩn của tài liệu tri thức và giữ Qdrant local làm kho vector. Qdrant không phải mô hình AI; giữ nó giúp project tự quản lý bộ lọc role và vẽ được mạng láng giềng embedding. Nếu muốn bỏ cả Qdrant thì phải đổi sang Gemini File Search, nhưng sẽ giảm quyền kiểm soát và khó dựng quả cầu từ các vector/quan hệ thực.
- Dùng `gemini-3.1-flash-lite` cho chat và `gemini-embedding-2` cho embedding. Khóa model/dimension trong cấu hình để lúc thay model có thể tạo bộ index mới, không trộn vector khác không gian.
- Bot chỉ trả lời trên những đoạn RAG đã truy xuất trong phạm vi role. Không cho Gemini tự tạo đơn, sửa tồn, phát hành hóa đơn hoặc thay đổi quyền.

## Hiện trạng của project

- Spring Boot 4.1.1, Java 17, Thymeleaf, PostgreSQL, JWT và Docker Compose.
- Admin đã có màn hình quản lý nguồn tri thức; nguồn mẫu được chia theo `customer`, `store-staff`, `warehouse`, `admin` và lưu trong PostgreSQL.
- Chat hiện có truy xuất từ khóa; Ollama là tùy chọn sinh câu trả lời. Mục tiêu cắt chuyển là bỏ hoàn toàn luồng Ollama và chuyển retrieval + generation sang Gemini.
- Quả cầu hiện tại chưa phản ánh tọa độ embedding. Mạng kết nối ở bản hoàn chỉnh phải được tính từ các láng giềng vector thật, không vẽ quan hệ giả từ tên/từ khóa.

## Sơ đồ luồng sau khi cắt chuyển

```text
Admin tạo/sửa nguồn
  └─ PostgreSQL: nội dung chuẩn, role, trạng thái, content hash, model/dimension
       └─ worker index nền
            ├─ chia đoạn → Gemini Embedding 2 → Qdrant local
            └─ cập nhật trạng thái INDEXED / FAILED trong PostgreSQL

Người dùng → widget chat → Spring Security/JWT
                              ├─ lấy role từ server, không tin role gửi từ trình duyệt
                              ├─ Gemini Embedding 2: vector câu hỏi
                              ├─ Qdrant: lọc roleKey + active=true, lấy top 5–8 đoạn
                              ├─ PostgreSQL: xác nhận lại nguồn còn bật và đúng role
                              └─ Gemini 3.1 Flash-Lite: trả lời kèm tên nguồn

Admin → API đồ thị → Qdrant: láng giềng gần nhất trong cùng role
                    └─ giao diện quả cầu chiếu node/edge để quản trị
```

## Quyết định dùng free tier và giới hạn dữ liệu

- Google hiện liệt kê `gemini-3.1-flash-lite` và `gemini-embedding-2` trong Standard Free Tier. Mức RPM/TPM/RPD phụ thuộc project, model và tài khoản, có thể đổi; cần xem quota thật trong AI Studio và xử lý `429 RESOURCE_EXHAUSTED`.
- Free tier ghi rõ nội dung được dùng để cải thiện sản phẩm của Google; paid tier ghi không dùng cho mục đích đó. Vì vậy chỉ bật Gemini free cho prototype với tài liệu công khai/đã làm sạch. Không gửi password, token, số điện thoại/email/địa chỉ khách, dữ liệu đơn hàng, hồ sơ khách hoặc bí mật kinh doanh.
- Tài liệu nội bộ cho kho/cửa hàng/admin chỉ được đưa lên free tier nếu đã được rà soát và chấp nhận điều khoản xử lý dữ liệu này. Nếu không chấp nhận, phải dùng tier có cam kết dữ liệu phù hợp hoặc Vertex AI trước khi bật các role đó.
- Không đưa giá/tồn/đơn/điểm thưởng cá nhân vào vector index. Nếu chatbot cần dữ liệu thay đổi theo thời gian, backend truy vấn nghiệp vụ và chỉ trả kết quả theo mẫu an toàn; không truyền bản ghi khách hàng thô sang Gemini.
- API key lấy từ Google AI Studio, lưu bằng biến môi trường `GEMINI_API_KEY` ở backend. Không commit key, không nhúng key vào JavaScript/HTML, không ghi key hoặc prompt đầy đủ vào log.

## Kế hoạch triển khai theo giai đoạn

### 1. Xác nhận API, quota và bảo vệ khóa

1. Tạo API key trong Google AI Studio và giữ ở biến môi trường của tiến trình Spring.
2. Kiểm tra trong AI Studio tài khoản/project được cấp free quota cho `gemini-3.1-flash-lite` và `gemini-embedding-2`; giới hạn hiện tại là theo project, model và tier.
3. Tạo cấu hình `GEMINI_API_KEY`, `ASSISTANT_CHAT_MODEL=gemini-3.1-flash-lite`, `ASSISTANT_EMBEDDING_MODEL=gemini-embedding-2`, dimension cố định (đề xuất 768) và collection versioned.
4. Khi thiếu key, ứng dụng vẫn mở trang web nhưng chatbot báo chưa cấu hình; không âm thầm chạy model cũ.

Ví dụ cấu hình Spring dự kiến (đặt trong cấu hình Gemini chỉ được bật khi key có mặt; không điền key trực tiếp vào file):

```properties
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.chat.model=${ASSISTANT_CHAT_MODEL:gemini-3.1-flash-lite}
spring.ai.google.genai.embedding.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.embedding.text.model=${ASSISTANT_EMBEDDING_MODEL:gemini-embedding-2}
spring.ai.google.genai.embedding.text.dimensions=${ASSISTANT_EMBEDDING_DIMENSIONS:768}
```

Trong ứng dụng, cấu hình Gemini chỉ được nạp khi key có mặt; provider/service vẫn phải xử lý trạng thái thiếu key để website khởi động và bot báo chưa cấu hình. Trên PowerShell, key được đặt ở môi trường của terminal chạy Spring bằng `$env:GEMINI_API_KEY = "..."`; không gửi giá trị key qua chat hoặc commit vào Git.

### 2. Nối Spring AI với Gemini và Qdrant

1. Dùng `spring-ai-starter-model-google-genai`, `spring-ai-starter-model-google-genai-embedding` và `spring-ai-starter-vector-store-qdrant`; chốt BOM tương thích Spring Boot 4.1.1 trước khi đổi `pom.xml`.
2. Cấu hình chat bằng `spring.ai.google.genai.api-key` và `spring.ai.google.genai.chat.model`.
3. Cấu hình embedding bằng `spring.ai.google.genai.embedding.api-key`, model `gemini-embedding-2` và dimension đã chọn. Tài liệu Spring AI embedding hiện có ví dụ model cũ; trước khi triển khai phải chạy thử chính xác model Gemini Embedding 2 trên starter đang dùng. Nếu starter chưa chuyển được model/options, viết adapter nhỏ gọi Google GenAI Java SDK; không quay về BGE-M3 hoặc model cũ.
4. Dùng một collection Qdrant có metadata `sourceId`, `roleKey`, `active`, `contentHash`, `chunkIndex`, `embeddingModel`, `embeddingDimensions`, `indexedAt`. Bộ lọc role luôn do backend dựng.
5. Đưa Qdrant lên Compose với named volume, cổng chỉ bind `127.0.0.1`, health check và không bật UI/API ra mạng công cộng.

### 3. Đồng bộ nguồn và index Gemini

1. PostgreSQL tiếp tục là nơi duy nhất admin tạo/sửa/bật/tắt/xóa tài liệu. Bổ sung trạng thái `PENDING/INDEXED/FAILED/DISABLED`, content hash, model, dimension, số đoạn, thời gian index và thông báo lỗi đã làm sạch.
2. Khi transaction lưu nguồn commit, worker nền đưa nguồn vào hàng chờ. Tách worker index khỏi request web; giới hạn concurrency và tốc độ gọi theo quota miễn phí.
3. Chuẩn hóa Markdown, giữ tiêu đề/đầu mục và chia đoạn khoảng 400–700 token; đoạn phải dưới giới hạn 8.192 token của Gemini Embedding 2. Gắn nguồn/role/hash vào metadata.
4. Dùng ID vector ổn định theo `sourceId + contentHash + chunkIndex`; index lặp không tạo bản sao. Khi nguồn sửa, xóa vector version cũ sau khi index mới thành công; khi tắt/xóa nguồn, loại vector cũ và kiểm tra lại trạng thái ở PostgreSQL.
5. Đặt hướng dẫn embedding nhất quán cho tài liệu và câu hỏi. `gemini-embedding-2` không dùng `task_type` như model đời trước; theo hướng dẫn Google, task instruction phải nằm trong nội dung đầu vào.
6. Có API index một nguồn/index tất cả, API trạng thái, retry có backoff cho timeout/429 và hiển thị số đang chờ, thành công, lỗi. Không gửi lại vô hạn khi quota hết.
7. Không dùng Batch API cho đợt index đầu theo giả định free: xác minh giá/quota hiện tại trước; trên free tier chỉ chạy queue chuẩn với tốc độ giới hạn.

### 4. Thay chatbot bằng semantic RAG Gemini

1. Controller tiếp tục lấy role từ `Authentication`/JWT; request body chỉ có câu hỏi và lịch sử đã giới hạn, không có quyền chọn role hoặc tên collection.
2. Tạo embedding câu hỏi bằng đúng `gemini-embedding-2` và dimension đang dùng để index.
3. Search Qdrant top 5–8 đoạn, filter đồng thời `roleKey` và `active=true`, đặt ngưỡng similarity. Sau search, query PostgreSQL xác nhận từng `sourceId`; loại nguồn bị tắt, xóa hoặc chuyển role.
4. Gửi Gemini câu hỏi cùng các đoạn ngắn được phép và chỉ dẫn role. Xem tài liệu RAG là dữ liệu không đáng tin, chống prompt injection; không gửi toàn bộ kho hay lịch sử dài.
5. Trả câu trả lời tiếng Việt, kèm danh sách nguồn thực sự được dùng. Khi không đủ căn cứ, từ chối rõ; không khẳng định điều trị/hiệu quả mỹ phẩm ngoài nhãn và tài liệu đã duyệt.
6. Gemini lỗi/quá quota thì trả thông báo tạm thời, không gọi Ollama/Qwen hoặc BGE cũ để dự phòng. Không bật Google Search grounding vì chatbot cần bám tài liệu đã kiểm duyệt và tính năng này có giới hạn/chi phí riêng.
7. Chỉ bổ sung truy vấn giá/tồn/đơn qua dịch vụ nghiệp vụ sau một bước thiết kế riêng; dùng kết quả tối thiểu, không gửi PII hoặc cho model tự thực hiện thao tác ghi.

### 5. Quản trị nguồn, trạng thái và quả cầu embedding

1. Màn hình `/admin/assistant-knowledge` hiển thị role, nguồn, active, model/dimension, số đoạn, trạng thái index, thời gian cập nhật và lỗi; có nút index lại một nguồn/toàn bộ.
2. API đồ thị chỉ trả node nguồn đã index và cạnh láng giềng Qdrant thật. Không trả vector thô ra frontend. Mỗi cạnh phải cùng role; node tooltip dẫn về đúng nguồn.
3. Quả cầu có bộ lọc role/trạng thái/nguồn và chú giải rõ: cạnh biểu diễn gần nhau trong embedding, không khẳng định có quan hệ nhân quả hay thành phần.
4. Sau khi cần hiển thị vị trí embedding ổn định, tính PCA/UMAP theo batch và lưu snapshot; không chạy phép chiếu toàn bộ ngay mỗi lượt mở trang.
5. GraphRAG thành phần mỹ phẩm là giai đoạn riêng: lưu node/edge có provenance và duyệt admin (ví dụ `sản phẩm → chứa → thành phần`, `thành phần → cảnh báo kết hợp → thành phần`). Không suy ra quan hệ chuyên môn chỉ từ vector gần nhau.

### 6. Kiểm thử, cắt chuyển và dọn model cũ

1. Thêm bộ câu hỏi tiếng Việt cho từng role, câu hỏi ngoài phạm vi, prompt injection, tài liệu đã tắt/xóa, hai role có tài liệu canary và truy vấn không có bằng chứng.
2. Kiểm tra `GEMINI_API_KEY` thiếu/sai, timeout, 429, quota hết, embedding lỗi, Qdrant mất kết nối, index lại lặp và thay đổi dimension/model.
3. So sánh kết quả keyword hiện hữu với semantic Gemini trong giai đoạn kiểm chứng; bật cho admin trước, rồi kho/cửa hàng, cuối cùng khách hàng.
4. Khi kiểm chứng xong, cắt sang Gemini, bỏ hẳn đường gọi Ollama từ `RoleAssistantService`, xóa dependency/config Ollama/Qwen/BGE-M3 và service Ollama khỏi Compose/docs. Sau cutover không có fallback sang model cũ.
5. Re-index toàn bộ nguồn bằng Gemini Embedding 2 vào collection mới; không tái sử dụng vector từ model cũ. Giữ nguồn PostgreSQL và bản sao lưu collection đến khi bốn role qua kiểm tra.
6. Theo dõi chi phí/quota và thời gian phản hồi bằng metrics không chứa prompt, token, key hay dữ liệu khách. Đặt giới hạn output/context, throttle index và cơ chế tạm ngắt chat khi 429 kéo dài.

## Tiêu chí hoàn thành

- Không còn đường thực thi nào gọi Ollama/Qwen/BGE-M3 sau cutover; cấu hình mẫu và tài liệu không yêu cầu cài model local.
- Bốn role chỉ truy xuất nguồn đúng role, đã bật và còn tồn tại; giả role trong request không thay đổi kết quả.
- Gemini trả nguồn kèm câu trả lời; câu hỏi ngoài tài liệu hoặc Gemini hết quota được xử lý rõ ràng, không gọi model cũ.
- Admin xem được index model/version và đồ thị láng giềng embedding thật; cạnh không bị nhầm thành tri thức graph đã kiểm duyệt.
- Có thể xây lại Qdrant từ PostgreSQL; đổi model/dimension phải tạo collection mới và re-index.
- Dữ liệu dùng với free tier đã được làm sạch, không gồm PII hoặc bí mật nội bộ chưa được phê duyệt.

## Tài liệu tham khảo chính thức

- [Spring AI Google GenAI chat](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html)
- [Spring AI Google GenAI embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html)
- [Spring AI Qdrant](https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html)
- [Gemini API pricing và chính sách dữ liệu theo tier](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini Embedding 2](https://ai.google.dev/gemini-api/docs/embeddings)
- [Gemini API rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Vòng đời và deprecation của model Gemini](https://ai.google.dev/gemini-api/docs/deprecations)
