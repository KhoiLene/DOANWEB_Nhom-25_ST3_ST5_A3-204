# Kế hoạch nâng cấp RAG thành vector RAG và GraphRAG

## Hiện trạng của project

- Project dùng Spring Boot 4.1.1, Java 17, Thymeleaf, PostgreSQL, JWT và Docker Compose.
- Admin đã có màn hình quản lý nguồn tri thức. Nội dung được lưu trong PostgreSQL, chia theo `customer`, `store-staff`, `warehouse`, `admin`.
- Chatbot hiện truy xuất đoạn văn bằng từ khóa. Ollama là tùy chọn để diễn đạt lại câu trả lời; khi Ollama vắng mặt, chatbot trích đoạn tài liệu trực tiếp.
- Quả cầu hiện tại là sơ đồ quan hệ role → nguồn → khái niệm. Nó chưa phải biểu đồ tọa độ embedding và chưa được dùng để tìm kiếm.
- Bước nâng cấp sẽ giữ PostgreSQL làm nguồn dữ liệu chuẩn, thêm Qdrant làm chỉ mục vector có thể tạo lại từ PostgreSQL.

## Kiến trúc đề xuất

```text
Admin quản lý nguồn / sản phẩm
        │
        ├── PostgreSQL: dữ liệu gốc, trạng thái và lịch sử index
        │
        └── Ollama BGE-M3: chia đoạn và tạo embedding
                         │
                         ▼
                       Qdrant

Người dùng → Thymeleaf chat widget → Spring Security/JWT
                                      │
                                      ├── lấy role từ token đã xác thực
                                      ├── embedding câu hỏi bằng BGE-M3
                                      ├── tìm Qdrant với bộ lọc role
                                      ├── lấy dữ liệu giá/tồn/đơn trực tiếp từ PostgreSQL khi cần
                                      └── Qwen qua Ollama → câu trả lời có nguồn dẫn
```

### Công nghệ miễn phí

- Dùng Spring AI 2.0.2 BOM và các starter `spring-ai-starter-model-ollama` cùng `spring-ai-starter-vector-store-qdrant`. Dòng Spring AI 2.0.x hỗ trợ Spring Boot 4.1.x của project. Không dùng snapshot.
- Chạy Qdrant bằng Docker Compose, gắn named volume để dữ liệu tồn tại qua lần khởi động lại. Chỉ bind cổng quản trị/vector ra `127.0.0.1`; Qdrant mặc định không bật xác thực trong cấu hình local.
- Dùng `bge-m3` cho embedding tiếng Việt và `qwen2.5:7b` cho sinh câu trả lời. Ollama liệt kê BGE-M3 là model đa ngôn ngữ khoảng 1.2 GB; Qwen2.5 7B khoảng 4.7 GB. Có thể giữ Qwen2.5 1.5B hoặc 3B làm lựa chọn nhẹ hơn cho máy ít RAM.
- Ollama chạy ngay trên Windows; Spring hiện chạy ngoài Docker nên dùng `localhost:11434`. Nếu đưa Spring vào container sau này, đổi sang `host.docker.internal` hoặc hostname service trong cùng Docker network.

## Phân quyền tri thức

| Role | Phạm vi tài liệu được truy xuất |
|---|---|
| `ROLE_USER` | Thông tin mỹ phẩm đã duyệt, thành phần/cách dùng/cảnh báo trên nhãn, mua hàng, điểm thưởng và nhận hàng |
| `ROLE_STORE_STAFF` | POS, hóa đơn/biên nhận, đơn online của chi nhánh, tồn tại chi nhánh và quy trình nhận điều chuyển |
| `ROLE_WAREHOUSE` | Nhập/xuất/điều chỉnh kho, điều chuyển, đơn được định tuyến về kho và quy trình hoàn tất |
| `ROLE_ADMIN` | Quản trị danh mục, người dùng, chi nhánh/kho, phân quyền và quản lý nguồn tri thức |

Backend tiếp tục xác định role từ JWT/authority ở server. Không nhận role hoặc filter Qdrant từ trình duyệt. Mọi truy vấn vector bắt buộc có filter `roleKey` và `active=true`; role không có nguồn phù hợp nhận câu trả lời từ chối có căn cứ. Giá, tồn kho, trạng thái đơn và điểm hiện tại là dữ liệu động, phải đọc từ dịch vụ nghiệp vụ/PostgreSQL chứ không nhúng như dữ liệu tĩnh.

## Các bước triển khai

### 1. Hạ tầng và tương thích dependency

- Chốt Spring AI 2.0.2 BOM, hai starter Ollama và Qdrant; build cùng Boot 4.1.1 trước khi tiếp tục.
- Thêm Qdrant vào Compose với volume bền vững, health check và cổng chỉ nghe loopback.
- Khai báo model chat, model embedding, collection, timeout và chế độ tắt/mở vector RAG qua cấu hình môi trường.
- Không tự tải model lúc ứng dụng khởi động; tài liệu tải model thủ công để app không bị treo vì tải hàng GB.

### 2. Index nguồn tri thức hiện có

- PostgreSQL tiếp tục là nguồn chuẩn cho `assistant_knowledge_sources`; thêm phiên bản nội dung, hash, trạng thái `PENDING/INDEXED/FAILED`, thời gian index và model embedding.
- Chuẩn hóa Markdown/text, chia thành đoạn khoảng 500–800 token, overlap khoảng 60–100 token; không chia cắt tiêu đề và mục liệt kê nếu có thể.
- Mỗi đoạn trong Qdrant có ID ổn định từ `sourceId + version + chunkIndex`. Metadata gồm `sourceId`, `roleKey`, `active`, `sourceType`, `version`, `chunkIndex` và `embeddingModel`.
- Tạo index lần đầu cho bốn nguồn mẫu; sau đó admin tạo/sửa/bật/tắt/xóa tài liệu thì đồng bộ sau khi transaction PostgreSQL commit. Re-index phải idempotent và không tạo chunk trùng.
- Thêm nút “Index lại” và trạng thái lỗi trong màn hình admin; lỗi Qdrant không được làm mất tài liệu gốc trong PostgreSQL.

### 3. Chuyển truy vấn chatbot sang semantic RAG

1. Lấy role từ JWT như luồng hiện có.
2. Tạo embedding câu hỏi bằng đúng BGE-M3 dùng lúc index.
3. Tìm top 5–8 đoạn trong Qdrant, lọc bắt buộc theo role và trạng thái active; đặt ngưỡng similarity để từ chối khi tài liệu không đủ gần.
4. Đưa câu hỏi, vài đoạn truy xuất và tiêu đề nguồn vào `ChatClient` của Spring AI/Ollama. Không gửi toàn bộ kho tri thức hoặc lịch sử dài vào prompt.
5. Trả câu trả lời tiếng Việt cùng tên nguồn. Nếu model hoặc Qdrant tắt, dùng retrieval từ khóa hiện có làm fallback; nếu không tìm được ngữ cảnh, nói rõ chưa có thông tin.
6. Giữ chatbot ở chế độ tư vấn trong phiên bản đầu, không cho model tự gọi API ghi đơn, sửa tồn, xuất hóa đơn hay đổi quyền.

### 4. Bổ sung tri thức mỹ phẩm có cấu trúc

- Thêm trường sản phẩm: thương hiệu, thành phần, loại da phù hợp, công dụng theo nhãn đã duyệt, cách dùng và cảnh báo.
- Quản trị viên kiểm duyệt nội dung trước khi index. Dữ liệu thiếu nguồn hoặc chưa xác minh không được dùng làm khuyến nghị.
- Không dùng chatbot để chẩn đoán bệnh da liễu, kê điều trị, cam kết hiệu quả hoặc suy luận an toàn cho người có bệnh nền/mang thai.
- Có thể thêm nguồn hướng dẫn khách cho routine cơ bản, nhưng câu trả lời phải nêu sản phẩm/nguồn làm căn cứ.

### 5. Nâng cấp quả cầu tri thức

- Tab **Bản đồ embedding**: mỗi điểm là một đoạn tài liệu hoặc sản phẩm đã index; màu thể hiện role/loại nội dung; đường nối là các láng giềng có độ tương đồng cao. Dùng phép chiếu 3D PCA/UMAP chạy theo batch, không tính lại trong request chat.
- Tab **Knowledge graph**: bổ sung node và cạnh có provenance được admin duyệt, ví dụ `Sản phẩm → chứa → Thành phần`, `Sản phẩm → phù hợp với → Loại da`, `Thành phần → có cảnh báo khi kết hợp → Thành phần`.
- Bắt đầu lưu graph trong PostgreSQL bằng bảng node/edge thay vì thêm một database nữa. Chỉ làm GraphRAG sau khi dữ liệu thành phần/loại da/cảnh báo có cấu trúc và được duyệt.
- Khi user hỏi quan hệ nhiều bước, truy xuất vector trước rồi lấy thêm cạnh graph 1–2 bước; giới hạn số nút và luôn đính kèm nguồn cho từng quan hệ.
- Click node mở nguồn hoặc sản phẩm; lọc theo role, tài liệu, loại dữ liệu, trạng thái index. Không hiển thị vector thô như thể đó là quan hệ ngữ nghĩa có thể đọc trực tiếp.

### 6. Đánh giá và bật dần

- Bộ câu hỏi tiếng Việt cho từng role, gồm câu hỏi trong phạm vi, ngoài phạm vi, câu hỏi chứa chỉ dẫn giả mạo, hỏi tài liệu đã tắt và dữ liệu sản phẩm.
- Kiểm tra role bằng tài liệu “canary”: tài liệu chỉ dành cho kho phải không xuất hiện trong câu trả lời khách/cửa hàng/admin và ngược lại.
- Kiểm tra cập nhật/xóa tài liệu, re-index lặp, Qdrant/Ollama ngừng chạy, thiếu nguồn và câu hỏi cần giá/tồn động.
- Ghi log thời gian, role, ID nguồn và trạng thái index; không log mật khẩu, token, địa chỉ khách hoặc toàn bộ nội dung chat lâu dài.
- Bật cho admin trước, sau đó nhân viên kho/cửa hàng, cuối cùng khách hàng. Giữ feature flag để quay lại lexical RAG nếu vector service lỗi.

## Tiêu chí hoàn thành

- Tài liệu sửa trong admin được index lại; tài liệu tắt/xóa không còn được truy xuất.
- Bốn role chỉ nhận đoạn thuộc đúng phạm vi; role luôn lấy từ JWT, không thể giả qua request body.
- Câu trả lời có nguồn dẫn và từ chối khi không có căn cứ; giá/tồn/đơn lấy từ database hiện tại.
- Qdrant giữ dữ liệu sau restart Docker; index có thể tái tạo từ PostgreSQL.
- Chatbot vẫn hoạt động ở chế độ trích đoạn khi Ollama tắt, và fallback lexical khi Qdrant tạm lỗi.
- Quả cầu thể hiện node/edge thật từ index/graph, click được về nguồn; không chỉ thêm hiệu ứng trang trí.

## Tài liệu chính thức

- [Spring AI 2.0.2 và tương thích Spring Boot](https://docs.spring.io/spring-ai/reference/2.0/getting-started.html)
- [Spring AI Ollama chat](https://docs.spring.io/spring-ai/reference/2.0/api/chat/ollama-chat.html)
- [Spring AI Ollama embeddings](https://docs.spring.io/spring-ai/reference/2.0/api/embeddings/ollama-embeddings.html)
- [Spring AI Qdrant vector store](https://docs.spring.io/spring-ai/reference/2.0/api/vectordbs/qdrant.html)
- [Qdrant local quickstart](https://qdrant.tech/documentation/quickstart/)
- [BGE-M3 trên Ollama](https://ollama.com/library/bge-m3)
- [Qwen2.5 trên Ollama](https://ollama.com/library/qwen2.5)
