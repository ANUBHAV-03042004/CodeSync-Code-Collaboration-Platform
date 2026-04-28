package com.notificationservice.codesync.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.from-email:noreply@codesync.io}")
    private String fromEmail;

    @Value("${notification.from-name:CodeSync}")
    private String fromName;

    @Value("${notification.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendTemplatedEmail(String toEmail, String subject,
                                    String templateName, Map<String, Object> variables) {
        try {
            Context ctx = new Context();
            ctx.setVariables(variables);
            ctx.setVariable("frontendBaseUrl", frontendBaseUrl);
            ctx.setVariable("appName", "CodeSync");
            String htmlBody = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {} [template={}]", toEmail, templateName);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    public void sendMentionEmail(String toEmail, String mentionedBy,
                                  String commentContent, String deepLinkUrl) {
        sendTemplatedEmail(toEmail, "@" + mentionedBy + " mentioned you on CodeSync",
                "mention-notification",
                Map.of("mentionedBy", mentionedBy,
                       "commentContent", commentContent,
                       "deepLinkUrl", deepLinkUrl));
    }

    public void sendSessionInviteEmail(String toEmail, String invitedBy,
                                        String projectName, String sessionUrl) {
        sendTemplatedEmail(toEmail, invitedBy + " invited you to a CodeSync session",
                "session-invite",
                Map.of("invitedBy", invitedBy,
                       "projectName", projectName,
                       "sessionUrl", sessionUrl));
    }
}