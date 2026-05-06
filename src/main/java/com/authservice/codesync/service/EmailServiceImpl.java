package com.authservice.codesync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.name:CodeSync}")
    private String appName;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(appName + " – Reset Your Password");

            String htmlBody = buildResetEmailHtml(resetLink);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            // Don't propagate — caller should not leak whether the email exists
        }
    }

    // ── HTML template ─────────────────────────────────────────────────────────

    private String buildResetEmailHtml(String resetLink) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Reset Your Password</title>
                  <style>
                    body { font-family: Arial, sans-serif; background:#f4f4f4; margin:0; padding:0; }
                    .container { max-width:600px; margin:40px auto; background:#fff;
                                 border-radius:8px; overflow:hidden;
                                 box-shadow:0 2px 8px rgba(0,0,0,.1); }
                    .header { background:#1e293b; padding:28px 32px; }
                    .header h1 { color:#fff; margin:0; font-size:22px; }
                    .body { padding:32px; color:#334155; line-height:1.6; }
                    .btn { display:inline-block; margin:24px 0; padding:14px 28px;
                           background:#6366f1; color:#fff; text-decoration:none;
                           border-radius:6px; font-weight:bold; font-size:15px; }
                    .notice { font-size:13px; color:#94a3b8; margin-top:24px; }
                    .footer { background:#f8fafc; padding:16px 32px;
                              font-size:12px; color:#94a3b8; text-align:center; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="header">
                      <h1>&#128273; CodeSync</h1>
                    </div>
                    <div class="body">
                      <h2>Reset Your Password</h2>
                      <p>We received a request to reset the password for your CodeSync account.
                         Click the button below to choose a new password.</p>
                      <a class="btn" href="%s">Reset Password</a>
                      <p>This link expires in <strong>15 minutes</strong>.</p>
                      <p class="notice">
                        If you did not request a password reset, you can safely ignore this email.
                        Your password will not be changed.
                      </p>
                    </div>
                    <div class="footer">
                      &copy; 2025 CodeSync. All rights reserved.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(resetLink);
    }
}
