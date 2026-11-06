# SwiftRoute Operations

Ngôn ngữ nghiệp vụ dùng chung cho workspace doanh nghiệp, vận hành giao hàng, thu nhập tài xế và đối soát.

## Workspace doanh nghiệp

**Account role**:
Quyền cấp nền tảng của một user, chỉ dùng cho năng lực quản trị toàn hệ thống; không biểu diễn công việc của user trong một tenant.
_Avoid_: Global role, vai trò doanh nghiệp

**Tenant**:
Doanh nghiệp hoặc workspace độc lập sở hữu cấu hình vận hành, điểm lấy hàng và dữ liệu giao nhận của mình.
_Avoid_: Tài khoản, user

**Tenant membership**:
Quan hệ cấp quyền của một user trong một tenant, mang vai trò riêng và độc lập với trạng thái phê duyệt của tenant.
_Avoid_: Account role, tenant status

**Cấp workspace**:
Quá trình tạo tenant và xác nhận OWNER membership đã hoạt động; chỉ hoàn tất khi cả hai phía Order và Auth cùng xác nhận.
_Avoid_: Phê duyệt doanh nghiệp, kích hoạt vận hành

**Onboarding doanh nghiệp**:
Tiến độ hoàn thiện hồ sơ, điểm lấy hàng, phương thức và cấu hình giao hàng trước khi gửi xét duyệt.
_Avoid_: Phê duyệt, kích hoạt subscription

**Hồ sơ xét duyệt**:
Snapshot bất biến của thông tin doanh nghiệp và điểm lấy hàng được OWNER gửi cho quản trị viên hệ thống tại một thời điểm.
_Avoid_: Tenant status, hồ sơ đang chỉnh sửa

**Phê duyệt doanh nghiệp**:
Quyết định hành chính của System Admin về một hồ sơ xét duyệt; không đồng nghĩa doanh nghiệp đã đủ mọi điều kiện vận hành.
_Avoid_: Cấp workspace, sẵn sàng vận hành

**Sẵn sàng vận hành**:
Kết quả tính từ trạng thái phê duyệt và các điều kiện nghiệp vụ thực tế như điểm lấy hàng, phương thức giao và năng lực tài xế.
_Avoid_: Subscription entitlement, tenant approval

## Tài chính vận hành

**Cước giao hàng**:
Khoản doanh nghiệp hoặc người nhận phải trả cho dịch vụ vận chuyển; không đồng nghĩa với thu nhập tài xế.
_Avoid_: Tiền tài xế, tiền công

**COD**:
Khoản tài xế thu hộ cho doanh nghiệp khi giao hàng; COD không phải thu nhập của tài xế.
_Avoid_: Doanh thu tài xế, tiền công

**Thu nhập tài xế**:
Khoản thù lao được ghi nhận cho một nhiệm vụ vận chuyển theo chính sách đã chốt tại thời điểm hoàn tất.
_Avoid_: COD, cước giao hàng, lương

**Chính sách thu nhập**:
Bộ tham số của một doanh nghiệp dùng để xác định thù lao nhiệm vụ, gồm phí cơ bản, đơn giá khoảng cách, mức tối thiểu và phụ phí.
_Avoid_: Bảng giá khách hàng

**Đối soát tài xế**:
Một kỳ tổng hợp các khoản thu nhập đã ghi nhận của một tài xế để doanh nghiệp duyệt và xác nhận thanh toán.
_Avoid_: Bảng lương, ví tài xế

**Khoảng cách tính thu nhập**:
Khoảng cách được chốt cho nhiệm vụ và lưu cùng phép tính thu nhập; không phải tổng quãng đường GPS tài xế thực tế đã di chuyển.
_Avoid_: Lịch sử GPS, quãng đường đồng hồ xe
