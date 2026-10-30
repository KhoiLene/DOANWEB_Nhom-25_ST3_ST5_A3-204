# Cẩm nang thao tác cho nhân viên kho

Phạm vi: các màn hình kho được giao cho nhân viên kho. Tài liệu không dạy lập trình và không cho phép truy cập kho/chi nhánh ngoài phạm vi được phân công.

## 1. Đăng nhập và kiểm tra kho

1. Đăng nhập bằng tài khoản nhân viên kho.
2. Kiểm tra tên kho hiển thị trên đầu trang.
3. Nếu thiếu tên kho hoặc sai kho, dừng thao tác và nhờ admin gán đúng kho.
4. Dùng tab **Tồn kho & nhập hàng** để ghi nhận hàng, xem tồn, lập phiếu điều chuyển; dùng **Đơn giao từ kho** để xử lý đơn được định tuyến tới kho.
5. Khi xem website như khách, mở menu hồ sơ và chọn **Về khu vực kho** để trở lại. Đăng xuất cũng nằm trong menu hồ sơ.

## 2. Nhập hàng vào kho

1. Mở **Tồn kho & nhập hàng** và kiểm tra đúng kho.
2. Trong biểu mẫu **Nhập hàng vào kho**, chọn sản phẩm từ danh sách.
3. Đếm hàng thực nhận, nhập số lượng là số nguyên dương.
4. Ghi mã lô và hạn dùng nếu nhãn hàng có các thông tin đó; nhập ghi chú khi cần truy vết nhà cung cấp, tình trạng hoặc chứng từ.
5. Đối chiếu sản phẩm, số lượng, lô và hạn dùng với hàng thực tế.
6. Bấm **Ghi nhận nhập kho** một lần rồi đợi trang tải lại.
7. Kiểm tra bảng tồn và lịch sử biến động để xác nhận giao dịch đã được ghi.

Không ghi nhận số lượng dự kiến trước khi kiểm đếm. Nếu lô/hạn dùng không khớp, tách riêng hàng có vấn đề và theo quy trình quản lý kho; không tự sửa nhãn sản phẩm.

## 3. Lập phiếu điều chuyển đến chi nhánh

1. Tại bảng **Tồn kho & điều chuyển**, tìm đúng sản phẩm.
2. Nhập số lượng cần chuyển, không vượt quá số tồn hiển thị.
3. Chọn chính xác chi nhánh nhận; thêm ghi chú nếu có yêu cầu vận chuyển hoặc đối chiếu.
4. Bấm **Tạo phiếu** một lần.
5. Xem **Lịch sử điều chuyển**: kiểm tra mã phiếu, sản phẩm, nơi nhận, số lượng và trạng thái.
6. Đóng gói, bàn giao theo quy trình nội bộ và giữ lại thông tin phiếu để theo dõi.
7. Tồn chi nhánh nhận chỉ tăng sau khi nhân viên tại chi nhánh xác nhận đã nhận hàng. Nếu phiếu còn đang đi, không lập phiếu thứ hai để bù trừ khi chưa xác minh.

Nếu nhập nhầm chi nhánh/số lượng hoặc hệ thống báo lỗi, ghi lại mã phiếu (nếu có) và báo quản lý/admin. Không thử lặp nhiều lần khi chưa biết giao dịch trước có thành công không.

## 4. Xử lý đơn giao từ kho

1. Mở tab **Đơn giao từ kho**.
2. Đọc mã đơn, người nhận, địa chỉ, mặt hàng, số lượng và trạng thái.
3. Với đơn COD đang `PENDING`, kiểm tra khả năng lấy đủ hàng rồi dùng thao tác nhận xử lý nếu nút được hiển thị.
4. Với đơn chuyển khoản `PENDING` và `UNPAID`, đối chiếu tiền vào tài khoản theo mã đơn và số tiền. Khách báo đã chuyển không thay thế bước xác minh của kho.
5. Chỉ sau khi thấy giao dịch thực nhận mới bấm **Xác nhận đã nhận tiền**. Đây là xác nhận thủ công; mã VietQR không tự kết nối đối soát ngân hàng.
6. Sau khi trạng thái là `CONFIRMED`, dùng thao tác nhận xử lý để vào `PROCESSING`. Khi đã đóng gói và bàn giao thật, cập nhật `SHIPPING`; chỉ xác nhận giao khi khách/đơn vị giao đã hoàn tất.
7. Kiểm tra lại trạng thái sau mỗi bước và làm theo các nút đang được hệ thống cho phép. Không xác nhận giao trước khi hàng rời kho hoặc tới khách.

Chỉ những đơn có kho hoàn tất là kho được gán cho tài khoản mới thuộc phạm vi thao tác. Nếu hàng thiếu hoặc sai địa chỉ, dừng bước cập nhật và liên hệ quản lý; không tự chuyển đơn sang kho/chi nhánh khác.

## 5. Đơn chuyển khoản chờ đối chiếu

1. Mở khu vực **Đơn giao từ kho** và tìm hàng đợi **Chuyển khoản VietQR cần xác nhận**.
2. Kiểm tra mã đơn, khách, số tiền, nội dung chuyển khoản và thời điểm khách báo đã chuyển.
3. Mở kênh ngân hàng nội bộ để xác minh khoản tiền thực vào đúng tài khoản. Không dựa riêng vào ảnh chụp hoặc lời báo của khách.
4. Nếu giao dịch khớp, bấm **Xác nhận đã nhận tiền** một lần. Nếu chưa thấy tiền hoặc số tiền/nội dung không khớp, không xác nhận; liên hệ quản lý để đối chiếu.
5. Sau đó chuyển đơn sang xử lý theo nút tiếp theo đang có. Không coi đây là xác minh ngân hàng tự động.

## 6. Kiểm soát chất lượng, lô và sai lệch

- Khi nhận hàng, đối chiếu sản phẩm, SKU, số lượng, bao bì, mã lô và hạn dùng với chứng từ/nhãn.
- Đặt hàng hư hỏng hoặc không rõ nguồn gốc sang khu vực chờ xử lý theo quy trình của đơn vị; không ghi nhận là hàng bán được nếu chưa có quyết định.
- Ưu tiên kiểm tra hạn dùng gần khi soạn hàng theo quy định nội bộ. Giao diện hiện tại lưu mã lô/hạn dùng khi nhập; không tự động cam kết rằng hệ thống sẽ chọn lô theo hạn gần nhất.
- Nếu số tồn thực tế khác màn hình, đếm lại, ghi rõ mã sản phẩm và chênh lệch, rồi báo người có quyền điều chỉnh. Không nhập một số bù trừ khi chưa biết nguyên nhân.
- Bàn giao ca bằng danh sách đơn đang xử lý, phiếu đang đi, hàng sai lệch và việc cần làm tiếp theo.

## 7. Khi nào cần nhờ admin

- Tài khoản chưa được gán kho hoặc sai kho.
- Chuyển khoản không khớp hoặc tiền chưa vào nhưng khách yêu cầu xác nhận.
- Sản phẩm chưa có trong danh mục hoặc không thể chọn.
- Cần điều chỉnh quyền người dùng, điểm kho, cấu hình chi nhánh hoặc dữ liệu hệ thống.
- Cần hủy/sửa phiếu đã tạo nhưng giao diện không có chức năng đó.

Chatbot có thể giải thích các bước nhưng không xem tồn thực tế trực tiếp, không tự sửa đơn và không thay xác nhận của người nhận hàng.

## Câu hỏi mẫu

- Nhập một lô hàng mới theo từng bước thế nào?
- Làm sao điều chuyển hàng tới chi nhánh?
- Khi nào tồn kho chi nhánh nhận được cộng?
- Tôi nên làm gì khi tồn thực tế khác số trên màn hình?
- Tôi xác nhận đơn chuyển khoản ở đâu?
