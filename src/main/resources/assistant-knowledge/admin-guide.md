# Cẩm nang thao tác dành cho admin

Phạm vi: các chức năng quản trị đang có trên website. Chỉ dùng tài khoản admin; chatbot hướng dẫn thao tác, không tự thay đổi dữ liệu. Tài liệu này không chứa mã nguồn, hướng dẫn lập trình, cấu hình máy chủ hoặc bí mật kết nối.

## 1. Vào quản trị và quay lại đúng khu vực

1. Đăng nhập bằng tài khoản có role admin.
2. Trang **Trung tâm vận hành** là điểm bắt đầu cho quản trị.
3. Trên website bán hàng, mở menu hồ sơ rồi chọn **Về quản trị** để trở lại workspace admin.
4. Trong workspace admin, nút **Cửa hàng** mở trang bán hàng; hồ sơ/đăng xuất nằm trong menu tài khoản.
5. Trước thao tác ảnh hưởng tồn, đơn, role hoặc chi nhánh, xác nhận đúng bản ghi và phạm vi.

## 2. Tài khoản và role

1. Mở **Người dùng**.
2. Chọn tài khoản cần chỉnh và bấm chỉnh sửa.
3. Kiểm tra tên, điện thoại, trạng thái hoạt động và role.
4. Role nhân viên cửa hàng phải được gán một chi nhánh; role nhân viên kho phải được gán một kho. Khách hàng/admin không cần gán điểm vận hành theo luồng hiện tại.
5. Lưu rồi kiểm tra danh sách để xác nhận cập nhật.
6. Khi người dùng nghỉ việc hoặc cần tạm khóa, đổi trạng thái theo chính sách nội bộ; không dùng chung tài khoản.

Sai role/điểm gán có thể khiến nhân viên xem sai khu vực hoặc không làm được việc. Sau khi đổi role, yêu cầu người dùng đăng nhập lại và kiểm tra menu hiện ra đúng. Không gửi mật khẩu, mã xác nhận, khóa dịch vụ hoặc dữ liệu cá nhân vào kho chatbot.

## 3. Danh mục và sản phẩm

### Danh mục

Mở **Danh mục** để xem danh sách. Khi thêm/sửa danh mục, dùng tên dễ hiểu, chọn quan hệ cha nếu có, kiểm tra hiển thị ngoài cửa hàng. Xóa danh mục có thể ảnh hưởng sản phẩm liên quan; chỉ thực hiện khi đã kiểm tra và có phương án chuyển sản phẩm.

### Sản phẩm

1. Mở **Sản phẩm**, chọn tạo mới hoặc sửa.
2. Nhập tên, danh mục, SKU, mô tả, giá, ảnh và các cờ hiển thị theo biểu mẫu.
3. Chỉ ghi công dụng, thành phần và cảnh báo có nguồn xác nhận từ nhà sản xuất/nhãn hàng.
4. Kiểm tra giá, ảnh, dấu hoạt động/hiển thị và dữ liệu tồn trước khi đưa lên cửa hàng.
5. Mở trang chi tiết như khách để rà soát nội dung.

Không dùng chatbot để tự bịa thành phần hoặc tuyên bố điều trị bệnh. Nếu chưa có tài liệu sản phẩm, đánh dấu nguồn cần bổ sung và hỏi người phụ trách nhãn hàng.

## 4. Chi nhánh và kho

1. Mở **Cửa hàng** trong nhóm địa điểm để thêm/sửa chi nhánh.
2. Điền tên, địa chỉ, thành phố, liên hệ, tọa độ và khả năng nhận tại cửa hàng theo dữ liệu đã xác nhận.
3. Đặt marker đúng vị trí; nếu bản đồ không tìm được địa chỉ, nhập tọa độ đã kiểm tra.
4. Chỉ bật chi nhánh hoạt động/nhận pickup khi cửa hàng sẵn sàng xử lý đơn.
5. Mở **Kho**, thêm/sửa điểm kho, địa chỉ và trạng thái hoạt động.
6. Kiểm tra các tài khoản nhân viên được gán đúng điểm làm việc.

Không dùng vị trí thử nghiệm như địa chỉ thật. Tọa độ sai có thể khiến khách được gợi ý nhầm chi nhánh.

## 5. Đơn hàng và biên nhận

Mở **Đơn hàng**. Có thể lọc theo mã đơn/từ khóa khách hàng, trạng thái đơn, trạng thái thanh toán, hình thức nhận hàng và khoảng ngày; chọn sắp xếp mới nhất trước hoặc cũ nhất trước. Bấm **Lọc đơn**, mở chi tiết đúng bản ghi rồi mới thực hiện thao tác.

Với chuyển khoản VietQR, khách có thể báo đã chuyển nhưng việc này chưa xác nhận tiền. Mở hàng đợi ở đúng chi nhánh/kho hoặc chi tiết đơn, đối chiếu giao dịch thực nhận rồi mới bấm **Xác nhận đã nhận tiền**. Đây là thao tác thủ công; VietQR không tự đối soát tài khoản ngân hàng. Sau khi xác nhận, tiếp tục theo trạng thái/nút được hiển thị. Không hủy đơn đã thanh toán nếu chưa thực sự xử lý hoàn tiền.

Mở **Biên nhận** để tra cứu; mở chi tiết rồi chọn **Tải / in PDF** để lưu qua hộp thoại trình duyệt.

Đừng dùng thao tác trạng thái để thay cho xác nhận thanh toán chưa có chứng từ. Nếu đơn sai hoặc cần hoàn/hủy mà màn hình không có thao tác tương ứng, ghi lại mã đơn và xử lý theo chính sách được phê duyệt.

## 6. Quản lý kho tri thức của chatbot

1. Mở **Kho tri thức RAG**.
2. Chọn đúng phạm vi: khách hàng, cửa hàng, kho hoặc admin.
3. Thêm tài liệu đã được người phụ trách duyệt; đặt tiêu đề mô tả đúng nội dung, bỏ thông tin riêng tư và bí mật.
4. Rà soát nội dung để chắc chắn hướng dẫn tương ứng với màn hình thật.
5. Lập chỉ mục nguồn mới hoặc nguồn vừa sửa. Chờ trạng thái báo đã sẵn sàng.
6. Thử các câu hỏi mẫu ở từng role. Kiểm tra chatbot trích đúng nguồn và không đưa nội dung của role khác.
7. Tắt nguồn đã cũ; xóa chỉ sau khi xác nhận tài liệu thay thế đã được lập chỉ mục.

Một nguồn role admin không được gán sang customer. Không đưa password, token, thông tin khách hàng hoặc hướng dẫn lập trình vào kho. Khi chatbot chưa có căn cứ, cần bổ sung tài liệu chứ không ra lệnh chatbot đoán.

## 7. Khuyến mãi và giới hạn hiện tại

Biểu mẫu **Sản phẩm** hỗ trợ cấu hình ưu đãi gắn với sản phẩm, gồm phần trăm giảm, tên chương trình, đối tượng ALL/VIP và thời gian áp dụng. Kiểm tra giá cuối trên cửa hàng và POS sau khi lưu. Không hướng dẫn tạo mã giảm giá hoặc chiến dịch tổng hợp nhiều sản phẩm nếu không có màn hình tương ứng. Tài liệu RAG chỉ mô tả điều kiện đã được xác nhận và đang hiển thị.

## 8. Quy trình duyệt nội dung và xử lý sự cố

- Gán một người sở hữu cho mỗi tài liệu và ngày cần rà soát lại.
- Khi đổi quy trình, cập nhật nguồn gốc, nội dung, role và câu hỏi kiểm tra trước khi lập chỉ mục.
- Nếu nhân viên báo nội dung sai, tắt nguồn liên quan trước, xác minh quy trình hiện hành rồi sửa và lập chỉ mục lại.
- Nếu chatbot mất kết nối, kiểm tra trạng thái dịch vụ và cấu hình qua kênh quản trị riêng; không dán khóa truy cập vào chatbot, tài liệu hay cuộc trò chuyện.
- Ghi lại câu hỏi, role và nguồn liên quan để cải thiện nội dung; không lưu thông tin cá nhân không cần thiết.

## Câu hỏi mẫu

- Làm sao gán đúng chi nhánh cho nhân viên bán hàng?
- Khi nào nên tắt một tài liệu khỏi chatbot?
- Vì sao chatbot không được thấy tài liệu của role khác?
- Admin có thể tạo chương trình khuyến mãi trên màn hình hiện tại không?
