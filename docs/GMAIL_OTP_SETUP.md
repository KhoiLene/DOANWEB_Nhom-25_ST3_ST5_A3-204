# Cấu hình Gmail để gửi OTP

Luồng đăng ký và đặt lại mật khẩu gửi OTP qua SMTP. Không lưu Gmail App Password trong Git, `application.properties` hoặc ảnh chụp màn hình.

## 1. Tạo Google App Password

1. Đăng nhập tài khoản Gmail sẽ dùng để gửi thư.
2. Bật xác minh 2 bước cho tài khoản Google.
3. Mở trang quản lý Google Account, tìm mục App passwords và tạo mật khẩu ứng dụng cho ứng dụng `Techtra Beauty`.
4. Google chỉ hiện mật khẩu ứng dụng một lần. Sao chép 16 ký tự đó; không dùng mật khẩu Gmail thông thường.

## 2. Chạy từ IntelliJ IDEA

1. Mở `Run > Edit Configurations...` và chọn cấu hình chạy ứng dụng Spring Boot.
2. Trong `Environment variables`, thêm các biến sau. Thay giá trị mẫu bằng email và App Password của bạn:

```text
MAIL_HOST=smtp.gmail.com;MAIL_PORT=587;MAIL_USERNAME=your-address@gmail.com;MAIL_PASSWORD=your-16-character-app-password
```

3. Nhấn `Apply`, `OK`, rồi chạy lại ứng dụng.
4. Mở `Quên mật khẩu`, gửi tới một email đã đăng ký trong hệ thống. Mã gồm 6 số có hiệu lực 5 phút.

Có thể cấu hình thay vì đó tại **Admin > Cài đặt hệ thống > Gmail SMTP**. Mỗi giá trị DB không trống được ưu tiên riêng; các trường DB để trống lấy từ biến môi trường. Cấu hình SMTP được đọc khi gửi từng OTP nên thay đổi trong trang admin dùng ngay ở lần gửi kế tiếp. Đổi biến môi trường cần khởi động lại ứng dụng. Trang cài đặt chỉ hiển thị trạng thái và tài khoản gửi, không trả mật khẩu về trình duyệt.

## 3. Chạy bằng terminal PowerShell

```powershell
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="your-address@gmail.com"
$env:MAIL_PASSWORD="your-16-character-app-password"
.\mvnw.cmd spring-boot:run
```

Các biến môi trường chỉ áp dụng trong cửa sổ PowerShell hiện tại. Có thể xóa chúng sau khi dừng ứng dụng:

```powershell
Remove-Item Env:MAIL_HOST, Env:MAIL_PORT, Env:MAIL_USERNAME, Env:MAIL_PASSWORD -ErrorAction SilentlyContinue
```

## 4. Triển khai production

Thêm `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` vào phần Environment Variables/Secrets của nhà cung cấp hosting. Không đặt App Password vào biến công khai phía trình duyệt. Nếu Gmail từ chối gửi, kiểm tra App Password, trạng thái xác minh 2 bước và nhật ký ứng dụng; không gửi lại mật khẩu qua chat.

## Cách hoạt động

- OTP được tạo ngẫu nhiên, chỉ gửi khi SMTP đã cấu hình và email gửi thành công.
- OTP hết hạn sau 5 phút, chỉ dùng một lần và tối đa 5 lần nhập sai.
- Gửi lại mã bị giới hạn tối thiểu 60 giây.
- Nội dung gửi không tiết lộ email có tồn tại trong hệ thống hay không.
- OTP gửi bởi `SmtpMailService` dùng cùng cấu hình DB/biến môi trường như trạng thái hiển thị trong trang cài đặt.
- SMTP hiện chỉ dùng cho OTP. Gmail gửi thông báo đơn hàng ngoài luồng OTP và Zalo OA chưa được tích hợp; việc lưu thông tin Zalo không có nghĩa là đã gửi tin.
