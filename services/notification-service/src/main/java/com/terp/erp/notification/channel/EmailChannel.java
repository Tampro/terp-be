package com.terp.erp.notification.channel;

import com.terp.erp.notification.client.IamInternalClient.Smtp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.function.Function;

/**
 * Email (plain text, or a company's HTML template) through the SMTP IAM resolved for the company (its own when on, else the platform's). The
 * sender is built per send from that configuration with explicit timeouts (same settings as IAM's buildMailer).
 * Never logs the address, the subject or the body.
 */
@Slf4j
@Component
public class EmailChannel {
    private final Function<Smtp, JavaMailSender> senders;

    public EmailChannel() {
        this(EmailChannel::build);
    }

    EmailChannel(Function<Smtp, JavaMailSender> senders) {
        this.senders = senders;
    }

    public SendResult send(Smtp smtp, String to, String subject, String body) {
        return send(smtp, to, subject, body, false);
    }

    /** html: the body is a company's sanitised HTML template (sent as text/html); else plain text. */
    public SendResult send(Smtp smtp, String to, String subject, String body, boolean html) {
        if (smtp == null || smtp.host() == null || smtp.host().isBlank() || smtp.fromAddress() == null) {
            return SendResult.fail("NO_SMTP");
        }
        String from = smtp.fromName() == null || smtp.fromName().isBlank() ? smtp.fromAddress()
                : smtp.fromName() + " <" + smtp.fromAddress() + ">";
        try {
            JavaMailSender sender = senders.apply(smtp);
            if (html) {
                MimeMessage mime = sender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(mime, "UTF-8");
                h.setFrom(from);
                h.setTo(to);
                h.setSubject(subject);
                h.setText(body, true);
                sender.send(mime);
                return SendResult.ok();
            }
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            sender.send(m);
            return SendResult.ok();
        } catch (MessagingException e) {
            // A malformed address in the template's input: retrying the same message cannot fix it.
            return SendResult.fail("MESSAGE_INVALID");
        } catch (MailAuthenticationException e) {
            // Wrong credentials stay wrong until an admin fixes them.
            return SendResult.fail("SMTP_AUTH");
        } catch (MailException e) {
            log.info("SMTP send failed: {}", e.getClass().getSimpleName());
            return SendResult.retry("SMTP_ERROR");
        }
    }

    static JavaMailSender build(Smtp s) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(s.host());
        sender.setPort(s.port() == null ? 587 : s.port());
        sender.setDefaultEncoding("UTF-8");
        if (s.username() != null && !s.username().isBlank()) {
            sender.setUsername(s.username());
            sender.setPassword(s.password());
        }
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(sender.getUsername() != null));
        p.put("mail.smtp.starttls.enable", String.valueOf("TLS".equals(s.encryption())));
        p.put("mail.smtp.ssl.enable", String.valueOf("SSL".equals(s.encryption())));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
