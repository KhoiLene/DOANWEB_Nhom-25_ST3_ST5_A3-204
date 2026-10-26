# Cẩm nang thao tác cho nhân viên cửa hàng

Phạm vi: hướng dẫn sử dụng các màn hình dành cho tài khoản nhân viên cửa hàng. Các bước bên dưới mô tả đúng chức năng hiện có. Tài liệu không bao gồm lập trình, mã nguồn hay cách truy cập dữ liệu ngoài phạm vi chi nhánh được giao.

## 1. Vào đúng khu vực làm việc

1. Đăng nhập bằng tài khoản nhân viên cửa hàng.
2. Trang **Đơn hàng & tồn kho** hiển thị dữ liệu chi nhánh được gán cho tài khoản.
3. Dùng tab **Bán tại quầy** để tạo giao dịch POS.
4. Khi mở cửa hàng trực tuyến để xem như khách, menu hồ sơ có mục **Về cửa hàng** để quay lại trang bán hàng nội bộ. Đăng xuất nằm trong menu hồ sơ.

Nếu hiện thông báo chưa được gán chi nhánh, không tự chọn chi nhánh khác. Báo admin gán tài khoản đúng chi nhánh.

## 2. Bán hàng tại quầy và tạo biên nhận

1. Mở tab **Bán tại quầy**.
2. Kiểm tra tên chi nhánh ở đầu trang; danh sách sản phẩm và số tồn là của chi nhánh đó.
3. Nhập số lượng cần bán vào ô cạnh sản phẩm. Số lượng phải từ 1 trở lên và không vượt tồn đang hiển thị.
4. Bấm **Thêm**. Sản phẩm, số lượng và thành tiền sẽ xuất hiện trong giỏ tại quầy.
5. Muốn bỏ một dòng, bấm **Xóa** tại dòng đó. Kiểm tra tổng tiền trước khi thu tiền.
6. Nhập tên/số điện thoại khách nếu khách đồng ý; hai trường này không bắt buộc. Chỉ ghi thông tin cần thiết cho giao dịch.
7. Chọn phương thức thanh toán phù hợp với khoản đã nhận: tiền mặt, thẻ, chuyển khoản hoặc khác.
8. Đọc lại sản phẩm, số lượng, tổng tiền với khách rồi bấm **Hoàn tất và xuất biên nhận** một lần.
9. Đợi trang biên nhận mở ra. Kiểm tra mã biên nhận, chi nhánh, khách, dòng hàng, số lượng, giá và phương thức thanh toán.
10. Bấm **Tải / in PDF**. Trong hộp thoại của trình duyệt, chọn máy in hoặc **Lưu dưới dạng PDF**, vị trí lưu và tên file; xác nhận lưu. Bản in phù hợp để đưa khách.
11. Quay lại POS để bán giao dịch tiếp theo.

Nếu bấm xong mà có lỗi, không bấm liên tiếp nhiều lần. Kiểm tra giỏ và biên nhận trước để tránh tạo giao dịch trùng. Nếu tồn thấp hơn số cần bán, giảm số lượng hoặc nhờ kho điều chuyển; không ghi nhận bán vượt tồn.

## 3. Xử lý đơn trực tuyến của chi nhánh

1. Mở **Đơn hàng & tồn kho**.
2. Xem người nhận, địa chỉ, sản phẩm, trạng thái và chỉ xử lý đơn của chi nhánh được giao.
3. Đơn `PENDING`: kiểm tra nội dung và khả năng xử lý, rồi bấm **Nhận xử lý**.
4. Đơn `PROCESSING`: khi đã chuẩn bị để bàn giao, bấm **Bàn giao giao hàng**.
5. Đơn `SHIPPING`: sau khi giao xong theo quy trình, bấm **Xác nhận giao**.
6. Kiểm tra trạng thái mới sau từng bước. Không bỏ qua trạng thái, tự chuyển sang kho hoặc xác nhận giao khi hàng chưa được giao.

Đơn thuộc kho trung tâm được nhân viên kho xử lý, không xuất hiện trong danh sách đơn của cửa hàng. Nếu đơn sai chi nhánh hoặc thông tin thiếu, báo quản lý/admin; không sửa dữ liệu ngoài màn hình được cấp.

## 4. Nhận hàng điều chuyển từ kho

1. Trong **Đơn hàng & tồn kho**, xem khu vực **Nhận điều chuyển**.
2. Đối chiếu mã phiếu, sản phẩm, kho gửi, số lượng và tình trạng kiện hàng thực tế.
3. Chỉ khi hàng đúng và đủ theo quy trình mới bấm **Xác nhận đã nhận**.
4. Sau đó kiểm tra bảng tồn kho chi nhánh để xác nhận số lượng được cập nhật.
5. Nếu thiếu, hư hỏng hoặc sai sản phẩm, không xác nhận thay cho số lượng thực nhận; ghi nhận theo quy trình nội bộ và báo kho/quản lý.

## 5. Xem tồn và lịch sử

Trang chi nhánh cho biết sản phẩm, SKU, tồn hiện tại, lịch sử biến động và phiếu điều chuyển. Tồn hiển thị là thông tin tại thời điểm tải trang, nên làm mới trước giao dịch lớn. Không cam kết tồn cho khách nếu chưa xác nhận trên hệ thống.

## 6. Biên nhận và bảo vệ thông tin

- POS tạo biên nhận sau khi giao dịch được chấp nhận; hóa đơn trực tuyến được phát hành khi đơn đã ghi nhận thanh toán.
- Chỉ xuất biên nhận cho đúng giao dịch. Kiểm tra đúng số tiền, phương thức và chi nhánh trước khi lưu/gửi.
- Không gửi danh sách khách hàng, số điện thoại hoặc đơn hàng vào nhóm chat công khai.
- Chatbot chỉ hướng dẫn thao tác; chatbot không tự bán hàng, không thay đổi trạng thái hay xác nhận thanh toán.

## 7. Khuyến mãi và câu hỏi ngoài chức năng hiện có

Ở phiên bản hiện tại, chưa có màn hình admin cấu hình chương trình khuyến mãi. Nhân viên không thể tự tạo mã, sửa mức giảm, điều kiện, thời gian hoặc cộng điểm từ POS. Khi khách hỏi về ưu đãi, kiểm tra thông tin đang hiển thị trên website/tài khoản và hỏi quản lý nếu chưa rõ; không tự hứa mức giảm hoặc quyền lợi.

## Câu hỏi mẫu

- Làm sao hoàn tất bán hàng và lưu biên nhận PDF?
- Tại sao tôi không thấy một đơn giao từ kho?
- Khi nào xác nhận đã nhận phiếu điều chuyển?
- Tôi làm gì nếu tồn trên màn hình thấp hơn số hàng khách muốn mua?
