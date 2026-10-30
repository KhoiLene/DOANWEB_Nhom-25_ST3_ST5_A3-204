# Hướng dẫn quản trị Techtra Beauty

Phạm vi trợ lý admin: vận hành chuỗi, đơn hàng, thanh toán, sản phẩm/ưu đãi, tài khoản, chi nhánh, kho, biên nhận, cài đặt và tài liệu chatbot.

## Cách trả lời và quyền hạn

- Trả lời tiếng Việt đơn giản, ngắn gọn, theo từng thao tác; ưu tiên các ý bắt đầu bằng →.
- Không dùng dấu sao/dấu thăng làm định dạng, không ghi “Nguồn:” và không trả lời bằng code.
- Không nói “tài liệu không có”. Nếu ngoài phạm vi quản lý hệ thống/chuỗi, lịch sự chỉ đúng bộ phận; không suy đoán.
- Chatbot chỉ hướng dẫn; không thay admin thao tác, không tự xác nhận thanh toán, đổi tồn, role, cấu hình hoặc trạng thái đơn.
- Không nhập mật khẩu, OTP, khóa tích hợp hay dữ liệu cá nhân khách vào tài liệu RAG.

## Trung tâm vận hành và luân chuyển tồn

- **Trung tâm vận hành** cho biết số chi nhánh/kho hoạt động, đơn cần tiếp nhận, tồn thấp, phiếu điều chuyển đang chờ và doanh thu đã thu trong tháng.
- Cảnh báo tồn dựa trên bán hàng gần đây, tồn khả dụng và nguồn kho đề xuất. Đọc đúng sản phẩm, chi nhánh, nguồn và số lượng trước khi duyệt điều chuyển.
- Admin duyệt tạo phiếu; chi nhánh xác nhận sau khi kiểm hàng thực tế. Việc duyệt không đồng nghĩa chi nhánh đã nhận.

## Đơn, thanh toán và biên nhận

- Trang **Đơn hàng** lọc được theo từ khóa/mã đơn, trạng thái, trạng thái thanh toán, hình thức nhận, khoảng ngày; có thể sắp xếp mới nhất trước hoặc cũ nhất trước.
- Mở chi tiết để xem thông tin trước khi cập nhật. Dùng đúng chuyển trạng thái được phép; đơn đã hủy không được mở lại.
- Hàng đợi VietQR thuộc chi nhánh/kho phụ trách. Lời báo “Tôi đã chuyển khoản” của khách chưa chứng minh tiền vào. Chỉ admin/nhân viên được phân công mới xác nhận sau khi kiểm tra giao dịch thật.
- VietQR hiện tạo thông tin/mã chuyển khoản; không được mô tả là tự động xác minh ngân hàng. Không hủy đơn đã thanh toán nếu chưa thực sự xử lý hoàn tiền.
- Mục **Biên nhận** dùng để tra cứu giao dịch và mở bản xem/in PDF.

## Danh mục quản trị

- **Sản phẩm**: cập nhật sản phẩm, thương hiệu, mô tả, giá, ảnh, thành phần/cảnh báo đã xác nhận và trạng thái hiển thị.
- Ưu đãi sản phẩm được cấu hình trong biểu mẫu sản phẩm: phần trăm giảm, tên chiến dịch, đối tượng tất cả/VIP và thời gian áp dụng. Hệ thống có thể hiển thị giá gốc, giá sau giảm và điều kiện tương ứng.
- Không hướng dẫn tạo mã giảm giá hoặc chiến dịch tổng hợp riêng nếu giao diện hiện hành không có.
- **Danh mục**: thêm/sửa nhóm sản phẩm; kiểm tra sản phẩm liên quan trước khi xóa.
- **Người dùng**: sửa role, trạng thái tài khoản và gán nhân viên cửa hàng cho chi nhánh hoặc nhân viên kho cho kho. Kiểm tra kỹ trước khi lưu.
- **Chi nhánh/Kho**: thêm/sửa địa chỉ và trạng thái; kiểm tra gợi ý địa chỉ/ghim và điểm làm việc trước khi bật hoạt động.

## Kho tri thức RAG và cài đặt

- Mục **Kho tri thức RAG** quản lý tài liệu theo customer, store-staff, warehouse, admin; chỉ nội dung đúng role mới được bật và lập chỉ mục.
- Quả cầu liên kết trực quan hóa tài liệu và láng giềng vector/embedding. Chức năng truy hồi hiện tại là Vector RAG, không gọi là GraphRAG.
- Sau khi sửa/thêm nguồn, lập chỉ mục và kiểm tra trạng thái. Thử câu hỏi bằng đúng role, bảo đảm câu trả lời theo chức năng thật và không lộ tài liệu chéo role.
- **Cài đặt hệ thống** lưu các tích hợp được hỗ trợ. Secret không được đọc ra hoặc gửi vào chatbot; không chia sẻ ảnh chụp có khóa.

## Câu hỏi mẫu

- Cách lọc đơn chuyển khoản chưa thanh toán?
- Khi nào được xác nhận đã nhận tiền?
- Làm sao duyệt điều chuyển hàng tới chi nhánh?
- Tài liệu chatbot được gán role và lập chỉ mục thế nào?
