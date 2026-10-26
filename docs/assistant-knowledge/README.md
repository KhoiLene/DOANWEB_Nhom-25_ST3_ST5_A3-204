# Nguồn tri thức chatbot

Các tệp Markdown ở đây là nội dung khởi đầu để nạp vào bốn vector store riêng. Trước khi dùng thật, quản trị viên cần rà soát SOP, chính sách và thông tin sản phẩm theo quy định hiện hành của doanh nghiệp rồi mới tải lên OpenAI.

| Vai trò | Tệp nguồn | Biến môi trường chứa vector store ID |
| --- | --- | --- |
| Khách hàng | `customer.md` | `OPENAI_VECTOR_STORE_CUSTOMER` |
| Nhân viên cửa hàng | `store-staff.md` | `OPENAI_VECTOR_STORE_STORE_STAFF` |
| Nhân viên kho | `warehouse.md` | `OPENAI_VECTOR_STORE_WAREHOUSE` |
| Admin | `admin.md` | `OPENAI_VECTOR_STORE_ADMIN` |

Tạo một vector store cho mỗi vai trò và tải riêng tệp tương ứng lên từng store bằng lệnh `python scripts/provision_assistant_vector_stores.py` sau khi đặt `OPENAI_API_KEY` trong môi trường cục bộ. Lệnh tạo bốn vector store và in các ID để cấu hình vào môi trường Spring. Chờ cả bốn tệp được lập chỉ mục trong OpenAI dashboard trước khi dùng chatbot. Không đưa khóa hoặc ID môi trường thật vào Git. Câu hỏi được gửi tới Responses API; backend chỉ đính kèm vector store của role lấy từ JWT đã xác thực.

## Cập nhật tri thức

1. Chủ sở hữu nghiệp vụ xác nhận nội dung và ngày hiệu lực.
2. Sửa đúng tệp Markdown theo vai trò.
3. Tải bản mới lên vector store đúng role và gỡ tệp cũ/hết hiệu lực.
4. Hỏi bộ câu hỏi kiểm tra ở cuối mỗi tệp, xác nhận câu trả lời có nguồn và không rò rỉ dữ liệu role khác.

Tệp truy xuất là dữ liệu tham khảo. Không đưa vào đây mật khẩu, khóa API, dữ liệu khách hàng, thông tin thanh toán hoặc chỉ dẫn bỏ qua kiểm soát quyền.
