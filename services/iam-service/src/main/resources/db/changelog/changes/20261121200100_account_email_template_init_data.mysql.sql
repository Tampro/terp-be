-- liquibase formatted sql

-- changeset ERP-IAM:20261121200100-1
-- Password reset and company email verification. Insert-if-missing: texts edited by the super admin are kept.
INSERT INTO email_template (code, name, from_name, created_by, created_date)
VALUES ('PASSWORD_RESET', 'Password Reset', '{app_name}', 'system', UTC_TIMESTAMP(6)),
       ('EMAIL_VERIFY', 'Email Verification', '{app_name}', 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE code = code;

-- changeset ERP-IAM:20261121200100-2
INSERT INTO email_template_content (template_id, lang, subject, content, created_by, created_date)
SELECT t.id, v.lang, v.subject, v.content, 'system', UTC_TIMESTAMP(6)
FROM email_template t
         JOIN (SELECT 'PASSWORD_RESET' AS code, 'vi' AS lang, 'Đặt lại mật khẩu {app_name}' AS subject,
                      '<p>Xin chào {user_name},</p><p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <b>{username}</b>.</p><p><a href="{reset_link}">Đặt lại mật khẩu</a></p><p>Liên kết có hiệu lực trong {expires_minutes} phút và chỉ dùng được một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>' AS content
               UNION ALL SELECT 'PASSWORD_RESET', 'en', 'Reset your {app_name} password',
                      '<p>Hello {user_name},</p><p>We received a request to reset the password of the account <b>{username}</b>.</p><p><a href="{reset_link}">Reset password</a></p><p>The link is valid for {expires_minutes} minutes and can be used once. If you did not ask for it, ignore this email.</p>'
               UNION ALL SELECT 'PASSWORD_RESET', 'ko', '{app_name} 비밀번호 재설정',
                      '<p>{user_name}님, 안녕하세요.</p><p><b>{username}</b> 계정의 비밀번호 재설정 요청을 받았습니다.</p><p><a href="{reset_link}">비밀번호 재설정</a></p><p>링크는 {expires_minutes}분 동안 한 번만 사용할 수 있습니다. 요청하지 않으셨다면 이 메일을 무시하세요.</p>'
               UNION ALL SELECT 'EMAIL_VERIFY', 'vi', 'Xác minh email của {company_name}',
                      '<p>Xin chào,</p><p>Vui lòng xác minh địa chỉ email của công ty {company_name} trên {app_name}.</p><p><a href="{verify_link}">Xác minh email</a></p><p>Liên kết có hiệu lực trong {expires_hours} giờ.</p>'
               UNION ALL SELECT 'EMAIL_VERIFY', 'en', 'Verify the email of {company_name}',
                      '<p>Hello,</p><p>Please verify the email address of {company_name} on {app_name}.</p><p><a href="{verify_link}">Verify email</a></p><p>The link is valid for {expires_hours} hours.</p>'
               UNION ALL SELECT 'EMAIL_VERIFY', 'ko', '{company_name} 이메일 인증',
                      '<p>안녕하세요.</p><p>{app_name}에서 {company_name}의 이메일 주소를 인증해 주세요.</p><p><a href="{verify_link}">이메일 인증</a></p><p>링크는 {expires_hours}시간 동안 유효합니다.</p>') v
              ON v.code = t.code
WHERE NOT EXISTS (SELECT 1 FROM email_template_content c WHERE c.template_id = t.id AND c.lang = v.lang);
