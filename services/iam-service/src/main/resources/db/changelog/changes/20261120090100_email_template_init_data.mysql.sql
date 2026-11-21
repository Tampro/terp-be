-- liquibase formatted sql

-- changeset ERP-IAM:20261120090100-1
-- Templates of the events that exist today. Insert-if-missing: texts edited later by the super admin are kept.
INSERT INTO email_template (code, name, from_name, created_by, created_date)
VALUES ('USER_CREATE', 'User Create', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('COMPANY_CREATE', 'Company Create', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('PLAN_ORDER_APPROVED', 'Plan Order Approved', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('PLAN_ORDER_REJECTED', 'Plan Order Rejected', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('PLAN_REQUEST_APPROVED', 'Plan Request Approved', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('PLAN_REQUEST_REJECTED', 'Plan Request Rejected', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('PAYOUT_APPROVED', 'Payout Approved', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('PAYOUT_REJECTED', 'Payout Rejected', '{app_name}', 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE code = code;

-- changeset ERP-IAM:20261120090100-2
INSERT INTO email_template_content (template_id, lang, subject, content, created_by, created_date)
SELECT t.id, v.lang, v.subject, v.content, 'system', UTC_TIMESTAMP(6)
FROM email_template t
         JOIN (SELECT 'USER_CREATE' AS code, 'vi' AS lang, 'Tài khoản {app_name} của bạn đã được tạo' AS subject,
                      '<p>Xin chào {user_name},</p><p>{company_name} đã tạo tài khoản {app_name} cho bạn.</p><p>Tên đăng nhập: <b>{username}</b><br>Mật khẩu tạm: <b>{temporary_password}</b></p><p>Bạn sẽ được yêu cầu đổi mật khẩu khi đăng nhập lần đầu tại {app_url}.</p>' AS content
               UNION ALL SELECT 'USER_CREATE', 'en', 'Your {app_name} account has been created',
                      '<p>Hello {user_name},</p><p>{company_name} created a {app_name} account for you.</p><p>Username: <b>{username}</b><br>Temporary password: <b>{temporary_password}</b></p><p>You will be asked to change it at your first login at {app_url}.</p>'
               UNION ALL SELECT 'USER_CREATE', 'ko', '{app_name} 계정이 생성되었습니다',
                      '<p>{user_name}님, 안녕하세요.</p><p>{company_name}에서 {app_name} 계정을 만들었습니다.</p><p>아이디: <b>{username}</b><br>임시 비밀번호: <b>{temporary_password}</b></p><p>{app_url}에서 처음 로그인할 때 비밀번호를 변경해야 합니다.</p>'
               UNION ALL SELECT 'COMPANY_CREATE', 'vi', 'Chào mừng {company_name} đến với {app_name}',
                      '<p>Xin chào {user_name},</p><p>Công ty {company_name} đã được tạo trên {app_name} và bạn là quản trị viên.</p><p>Tên đăng nhập: <b>{username}</b><br>Mật khẩu tạm: <b>{temporary_password}</b></p><p>Đăng nhập tại {app_url} và đổi mật khẩu ở lần đầu.</p>'
               UNION ALL SELECT 'COMPANY_CREATE', 'en', 'Welcome {company_name} to {app_name}',
                      '<p>Hello {user_name},</p><p>{company_name} has been set up on {app_name} and you are its administrator.</p><p>Username: <b>{username}</b><br>Temporary password: <b>{temporary_password}</b></p><p>Sign in at {app_url} and change the password at your first login.</p>'
               UNION ALL SELECT 'COMPANY_CREATE', 'ko', '{app_name}에 오신 것을 환영합니다, {company_name}',
                      '<p>{user_name}님, 안녕하세요.</p><p>{company_name}이(가) {app_name}에 등록되었으며 귀하가 관리자입니다.</p><p>아이디: <b>{username}</b><br>임시 비밀번호: <b>{temporary_password}</b></p><p>{app_url}에서 로그인하고 처음 로그인 시 비밀번호를 변경하세요.</p>'
               UNION ALL SELECT 'PLAN_ORDER_APPROVED', 'vi', 'Đơn {order_number} đã được duyệt',
                      '<p>Đơn {order_number} của {company_name} đã được duyệt.</p><p>Gói: <b>{plan_name}</b> ({billing_cycle})<br>Số tiền: {final_price}<br>Hiệu lực đến: {expires_at}</p>'
               UNION ALL SELECT 'PLAN_ORDER_APPROVED', 'en', 'Order {order_number} approved',
                      '<p>Order {order_number} of {company_name} has been approved.</p><p>Plan: <b>{plan_name}</b> ({billing_cycle})<br>Amount: {final_price}<br>Valid until: {expires_at}</p>'
               UNION ALL SELECT 'PLAN_ORDER_APPROVED', 'ko', '주문 {order_number}이(가) 승인되었습니다',
                      '<p>{company_name}의 주문 {order_number}이(가) 승인되었습니다.</p><p>요금제: <b>{plan_name}</b> ({billing_cycle})<br>금액: {final_price}<br>유효 기간: {expires_at}까지</p>'
               UNION ALL SELECT 'PLAN_ORDER_REJECTED', 'vi', 'Đơn {order_number} bị từ chối',
                      '<p>Đơn {order_number} (gói {plan_name}) của {company_name} đã bị từ chối.</p><p>Lý do: {notes}</p>'
               UNION ALL SELECT 'PLAN_ORDER_REJECTED', 'en', 'Order {order_number} rejected',
                      '<p>Order {order_number} ({plan_name}) of {company_name} has been rejected.</p><p>Reason: {notes}</p>'
               UNION ALL SELECT 'PLAN_ORDER_REJECTED', 'ko', '주문 {order_number}이(가) 거절되었습니다',
                      '<p>{company_name}의 주문 {order_number}({plan_name})이(가) 거절되었습니다.</p><p>사유: {notes}</p>'
               UNION ALL SELECT 'PLAN_REQUEST_APPROVED', 'vi', 'Yêu cầu gói {plan_name} đã được duyệt',
                      '<p>Yêu cầu gói <b>{plan_name}</b> ({billing_cycle}) của {company_name} đã được duyệt.</p><p>{notes}</p>'
               UNION ALL SELECT 'PLAN_REQUEST_APPROVED', 'en', 'Plan request {plan_name} approved',
                      '<p>The request of {company_name} for <b>{plan_name}</b> ({billing_cycle}) has been approved.</p><p>{notes}</p>'
               UNION ALL SELECT 'PLAN_REQUEST_APPROVED', 'ko', '{plan_name} 요금제 요청이 승인되었습니다',
                      '<p>{company_name}의 <b>{plan_name}</b> ({billing_cycle}) 요금제 요청이 승인되었습니다.</p><p>{notes}</p>'
               UNION ALL SELECT 'PLAN_REQUEST_REJECTED', 'vi', 'Yêu cầu gói {plan_name} bị từ chối',
                      '<p>Yêu cầu gói <b>{plan_name}</b> của {company_name} đã bị từ chối.</p><p>Lý do: {notes}</p>'
               UNION ALL SELECT 'PLAN_REQUEST_REJECTED', 'en', 'Plan request {plan_name} rejected',
                      '<p>The request of {company_name} for <b>{plan_name}</b> has been rejected.</p><p>Reason: {notes}</p>'
               UNION ALL SELECT 'PLAN_REQUEST_REJECTED', 'ko', '{plan_name} 요금제 요청이 거절되었습니다',
                      '<p>{company_name}의 <b>{plan_name}</b> 요금제 요청이 거절되었습니다.</p><p>사유: {notes}</p>'
               UNION ALL SELECT 'PAYOUT_APPROVED', 'vi', 'Yêu cầu rút hoa hồng đã được duyệt',
                      '<p>Yêu cầu rút {amount} của {company_name} đã được duyệt và thanh toán.</p><p>{notes}</p>'
               UNION ALL SELECT 'PAYOUT_APPROVED', 'en', 'Payout approved',
                      '<p>The payout of {amount} requested by {company_name} has been approved and paid.</p><p>{notes}</p>'
               UNION ALL SELECT 'PAYOUT_APPROVED', 'ko', '출금 요청이 승인되었습니다',
                      '<p>{company_name}의 {amount} 출금 요청이 승인되어 지급되었습니다.</p><p>{notes}</p>'
               UNION ALL SELECT 'PAYOUT_REJECTED', 'vi', 'Yêu cầu rút hoa hồng bị từ chối',
                      '<p>Yêu cầu rút {amount} của {company_name} đã bị từ chối. Số tiền đã được trả lại số dư.</p><p>Lý do: {notes}</p>'
               UNION ALL SELECT 'PAYOUT_REJECTED', 'en', 'Payout rejected',
                      '<p>The payout of {amount} requested by {company_name} has been rejected; the amount is back in the balance.</p><p>Reason: {notes}</p>'
               UNION ALL SELECT 'PAYOUT_REJECTED', 'ko', '출금 요청이 거절되었습니다',
                      '<p>{company_name}의 {amount} 출금 요청이 거절되었으며 금액은 잔액으로 반환되었습니다.</p><p>사유: {notes}</p>') v
              ON v.code = t.code
ON DUPLICATE KEY UPDATE template_id = email_template_content.template_id;

-- changeset ERP-IAM:20261120090100-3
-- Company admins manage which notifications their company receives.
INSERT INTO permission (code, module, description)
VALUES ('NOTIFICATION_SETTINGS_UPDATE', 'NOTIFICATION', 'Turn company email notifications on or off')
ON DUPLICATE KEY UPDATE module = VALUES(module), description = VALUES(description);

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code = 'NOTIFICATION_SETTINGS_UPDATE'
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
