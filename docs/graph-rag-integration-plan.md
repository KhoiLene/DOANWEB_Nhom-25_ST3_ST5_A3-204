# Gemini RAG theo role và đồ thị embedding

> Trạng thái: luồng Gemini chat, Gemini Embedding 2, Qdrant và quản lý chỉ mục đã được tích hợp. Kế hoạch bên dưới mô tả kiến trúc hiện hành và các phần mở rộng có thể làm sau.

## Mục tiêu và ranh giới

- Dùng Gemini Developer API cho chat và embedding; không gọi Ollama/Qwen/BGE-M3 hoặc OpenAI.
- PostgreSQL giữ nội dung nguồn tri thức và trạng thái index. Qdrant local giữ vector; có thể dựng lại Qdrant từ PostgreSQL.
- Chat xác định role từ Spring Security, lọc Qdrant theo `roleKey` và `active=true`, rồi kiểm tra lại trạng thái nguồn trong PostgreSQL.
- Bot chỉ trả lời dựa trên đoạn RAG đúng role. Nó không thực hiện thao tác đơn hàng, tồn kho, hóa đơn hoặc phân quyền.
- Admin có thể index một nguồn hoặc toàn bộ, xem trạng thái và mở quả cầu láng giềng embedding. Vector thô không gửi về browser.

## Luồng hiện tại

```text
Admin tạo/sửa/bật nguồn
  └─ PostgreSQL: nội dung, role, trạng thái, content hash, model/dimension
       └─ worker nền
            ├─ chia nội dung thành đoạn
            ├─ Gemini Embedding 2 → Qdrant
            └─ cập nhật INDEXED / PENDING / FAILED / DISABLED

Người dùng → widget → Spring Security
  ├─ lấy role từ Authentication
  ├─ Gemini Embedding 2 tạo vector câu hỏi
  ├─ Qdrant tìm top-k với role và active filter
  ├─ PostgreSQL xác nhận lại nguồn, role, hash và trạng thái index
  └─ Gemini Flash-Lite trả lời với các đoạn phù hợp và tên nguồn

Admin → API đồ thị → tìm láng giềng vector trong từng role → quả cầu tương tác
```

Project dùng `GeminiApiClient` gọi Gemini REST trực tiếp để chat và embedding. `GeminiEmbeddingModel` cắm adapter này vào Spring AI `VectorStore`; Qdrant là nơi lưu và tìm vector. Cách này hỗ trợ Gemini Embedding 2 và cho phép ứng dụng chạy khi API key chưa được cấu hình. Khi thiếu key, nguồn ở trạng thái `PENDING`, còn chatbot báo cần cấu hình thay vì dùng model khác.

## Cấu hình chạy

Tạo key trong Google AI Studio, rồi đặt key trong môi trường terminal chạy ứng dụng. Không ghi key vào repository hoặc browser:

```powershell
$env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
docker compose up -d qdrant
.\mvnw.cmd spring-boot:run
```

Mặc định: chat `gemini-3.1-flash-lite`, embedding `gemini-embedding-2`, dimension 768, collection `techtra_knowledge_gemini_embedding_2_768`. Biến có thể cấu hình gồm `ASSISTANT_CHAT_MODEL`, `ASSISTANT_EMBEDDING_MODEL`, `ASSISTANT_EMBEDDING_DIMENSIONS`, `ASSISTANT_QDRANT_COLLECTION`, `GEMINI_API_BASE_URL`, `GEMINI_TIMEOUT_SECONDS`, `ASSISTANT_RAG_TOP_K`, `ASSISTANT_RAG_SIMILARITY_THRESHOLD`, `QDRANT_HOST` và `QDRANT_GRPC_PORT`.

Đổi model hoặc dimension phải tạo collection mới và index lại toàn bộ. Không trộn vector của các embedding space khác nhau.

## Quản trị index và quả cầu

- Nguồn tri thức mẫu được lưu trong PostgreSQL tại `src/main/resources/assistant-knowledge/` và được nạp bởi ứng dụng khi khởi tạo database.
- Khi tạo/sửa/tắt/xóa nguồn, event sau commit gửi cập nhật tới worker index nền. ID vector ổn định theo nguồn, content hash và chunk index giúp index lặp không tạo bản sao.
- Nguồn mẫu cũ có thể hiện `PENDING` đến khi có key và Qdrant; dùng nút **Lập chỉ mục tất cả** để thử lại.
- Trang admin hiển thị trạng thái, model, dimension và số chunk; nguồn có thể index riêng lẻ. `FAILED` hiển thị lỗi đã làm sạch; không ghi API key hay nội dung prompt vào log.
- API `/api/admin/assistant-knowledge/graph` truy vấn láng giềng Qdrant trong cùng role. Đồ thị giới hạn số nguồn được truy vấn mỗi role để không tiêu tốn quota Gemini quá mức.
- Quả cầu hiển thị role, nguồn và cạnh embedding thật. Vị trí vẽ trên mặt cầu hiện là bố cục trực quan ổn định, chưa phải phép chiếu PCA/UMAP của tọa độ embedding. Cạnh có nghĩa là vector gần nhau, không khẳng định quan hệ nhân quả hay kiến thức chuyên môn.

## Giới hạn bảo mật và Free Tier

- Role không lấy từ request body. Backend áp dụng role filter, `active=true` filter, rồi kiểm tra trạng thái và hash nguồn tại PostgreSQL trước khi tạo prompt.
- Tài liệu được xem là dữ liệu không đáng tin, không phải chỉ dẫn cho model. Bot được yêu cầu từ chối khi tài liệu không đủ căn cứ.
- Email và số điện thoại được che trước khi gọi Gemini. Việc che này không nhận diện mọi loại dữ liệu cá nhân; vì vậy không tải thông tin khách hàng, đơn hàng, địa chỉ hoặc bí mật kinh doanh lên kho RAG.
- Gemini Free Tier có thể dùng nội dung để cải thiện sản phẩm; chỉ lập chỉ mục tài liệu công khai/đã duyệt và đã làm sạch. Hạn mức thay đổi tùy project/model. Nếu không chấp nhận cách xử lý dữ liệu free tier, không cấu hình key free cho dữ liệu nội bộ.
- Khi Gemini trả 429 hoặc lỗi API, ứng dụng báo lỗi rõ ràng và không fallback sang model cũ. Qdrant Compose chỉ bind cổng ra loopback.

## Phần mở rộng đề xuất

1. Thêm backoff có giới hạn, semaphore/rate limiter và hàng đợi có thể phục hồi để tránh dồn request lúc re-index nhiều nguồn; không tự retry liên tục khi quota cạn.
2. Thêm similarity score vào cạnh và bộ lọc trạng thái/role trong giao diện admin.
3. Nếu cần bố trí node theo vector thực, chạy PCA/UMAP nền và lưu snapshot versioned; không trả vector thô về client.
4. Xây Knowledge Graph có node/edge được quản trị viên duyệt và provenance riêng. Không suy ra quan hệ mỹ phẩm đáng tin cậy chỉ từ láng giềng embedding.
5. Nếu chatbot cần dữ liệu tồn kho, đơn hoặc điểm thưởng, gọi dịch vụ nghiệp vụ có phân quyền và chỉ gửi Gemini dữ liệu tối thiểu đã loại PII; thiết kế luồng này riêng với RAG tĩnh.

## Tài liệu chính thức

- [Gemini Embedding 2](https://ai.google.dev/gemini-api/docs/embeddings) và [Gemini API embeddings](https://ai.google.dev/api/embeddings)
- [Gemini pricing và xử lý dữ liệu theo tier](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) và [model deprecations](https://ai.google.dev/gemini-api/docs/deprecations)
- [Spring AI 2.0.x với Spring Boot 4.x](https://docs.spring.io/spring-ai/reference/2.1/getting-started.html)
- [Spring AI Qdrant vector store](https://docs.spring.io/spring-ai/reference/2.1/api/vectordbs/qdrant.html)
