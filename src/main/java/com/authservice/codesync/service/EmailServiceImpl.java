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

    @Value("${app.name:YoursCode}")
    private String appName;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Password reset ─────────────────────────────────────────────────────────

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(appName + " – Reset Your Password");
            helper.setText(buildResetEmailHtml(resetLink), true);

            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Email verification ─────────────────────────────────────────────────────

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String username, String verifyLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(appName + " – Verify Your Email Address");
            helper.setText(buildVerificationEmailHtml(username, verifyLink), true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── HTML templates ─────────────────────────────────────────────────────────

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
                    .header h1 { color:#FFD600; margin:0; font-size:22px; letter-spacing:.08em; }
                    .body { padding:32px; color:#334155; line-height:1.6; }
                    .btn { display:inline-block; margin:24px 0; padding:14px 28px;
                           background:#FFD600; color:#111; text-decoration:none;
                           font-weight:800; font-size:15px; text-transform:uppercase;
                           letter-spacing:.05em; }
                    .notice { font-size:13px; color:#94a3b8; margin-top:24px; }
                    .footer { background:#f8fafc; padding:16px 32px;
                              font-size:12px; color:#94a3b8; text-align:center; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="header"><h1>⚡ YOURSCODE</h1></div>
                    <div class="body">
                      <h2>Reset Your Password</h2>
                      <p>We received a request to reset the password for your YoursCode account.
                         Click the button below to choose a new password.</p>
                      <a class="btn" href="%s">Reset Password</a>
                      <p>This link expires in <strong>15 minutes</strong>.</p>
                      <p class="notice">
                        If you did not request a password reset, you can safely ignore this email.
                      </p>
                    </div>
                    <div class="footer">&copy; 2025 YoursCode. All rights reserved.</div>
                  </div>
                </body>
                </html>
                """.formatted(resetLink);
    }

    private String buildVerificationEmailHtml(String username, String verifyLink) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Verify Your Email</title>
                  <style>
                    body { font-family: Arial, sans-serif; background:#f4f4f4; margin:0; padding:0; }
                    .container { max-width:600px; margin:40px auto; background:#fff;
                                 border-radius:8px; overflow:hidden;
                                 box-shadow:0 2px 8px rgba(0,0,0,.1); }
                    .header { background:#1e293b; padding:28px 32px; }
                    .header h1 { color:#FFD600; margin:0; font-size:22px; letter-spacing:.08em; }
                    .body { padding:32px; color:#334155; line-height:1.6; }
                    .btn { display:inline-block; margin:24px 0; padding:14px 28px;
                           background:#FFD600; color:#111; text-decoration:none;
                           font-weight:800; font-size:15px; text-transform:uppercase;
                           letter-spacing:.05em; }
                    .url-fallback { word-break:break-all; font-size:12px; color:#64748b;
                                    background:#f1f5f9; padding:10px 14px; margin-top:8px; }
                    .notice { font-size:13px; color:#94a3b8; margin-top:24px; }
                    .footer { background:#f8fafc; padding:16px 32px;
                              font-size:12px; color:#94a3b8; text-align:center; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="header"><h1>⚡ YOURSCODE</h1></div>
                    <div class="body">
                      <h2>Welcome, %s! 👋</h2>
                      <p>Thanks for signing up. Please verify your email address to activate your
                         YoursCode account and start collaborating.</p>
                      <a class="btn" href="%s">Verify Email Address</a>
                      <p>Or copy and paste this link into your browser:</p>
                      <div class="url-fallback">%s</div>
                      <p>This link expires in <strong>24 hours</strong>.</p>
                      <p class="notice">
                        If you did not create a YoursCode account, you can safely ignore this email.
                      </p>
                    </div>
                    <div class="footer">&copy; 2025 YoursCode. All rights reserved.</div>
                  </div>
                </body>
                </html>
                """.formatted(username, verifyLink, verifyLink);
    }
}
