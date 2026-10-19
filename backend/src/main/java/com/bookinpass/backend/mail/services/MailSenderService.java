package com.bookinpass.backend.mail.services;

import io.awspring.cloud.ses.SimpleEmailServiceJavaMailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Envoi par AWS SES ({@code SendRawEmail}), trois essais espacés. L'appelant gère l'asynchronisme. */
@Slf4j
@Service
public class MailSenderService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 500;
    private static final int MAX_ATTACHMENT_SIZE_BYTES = 8_000_000;

    private final ObjectProvider<SimpleEmailServiceJavaMailSender> javaMailSender;

    public MailSenderService(ObjectProvider<SimpleEmailServiceJavaMailSender> javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    /**
     * @param tags étiquettes SES ({@code X-SES-MESSAGE-TAGS}), renvoyées sur les événements de remise
     * @return {@code true} si SES a accepté l'e-mail
     */
    public boolean sendEmailWithRetry(String type, String from, String personal, String to, Set<String> bcc, String subject,
                                      String body, Map<String, String> tags, Map<String, byte[]> attachments) {
        SimpleEmailServiceJavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mail [{}] not sent: SES is not configured (MAIL_ENABLED=false)", type);
            return false;
        }
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                send(sender, from, personal, to, bcc, subject, body, tags, attachments);
                log.info("Mail [{}] sent", type);
                return true;
            } catch (Exception e) {
                log.warn("Attempt {} to send mail [{}] failed: {}", attempt, type, e.getMessage());
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("All {} attempts to send mail [{}] failed", MAX_RETRIES, type);
        return false;
    }

    private static void send(SimpleEmailServiceJavaMailSender sender, String from, String personal, String to, Set<String> bcc,
                             String subject, String body, Map<String, String> tags, Map<String, byte[]> attachments)
            throws MessagingException {
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
        try {
            helper.setFrom(Objects.requireNonNull(from), personal);
        } catch (UnsupportedEncodingException e) {
            helper.setFrom(Objects.requireNonNull(from));
        }
        helper.setTo(Objects.requireNonNull(to));
        helper.setSubject(Objects.requireNonNull(subject));
        helper.setText(Objects.requireNonNull(body), true);
        if (bcc != null) {
            String[] hidden = bcc.stream().filter(b -> b != null && !b.isBlank()).toArray(String[]::new);
            if (hidden.length > 0) {
                helper.setBcc(hidden);
            }
        }
        if (attachments != null) {
            for (Map.Entry<String, byte[]> attachment : attachments.entrySet()) {
                if (attachment.getValue().length > MAX_ATTACHMENT_SIZE_BYTES) {
                    log.warn("Attachment {} skipped: {} bytes", attachment.getKey(), attachment.getValue().length);
                    continue;
                }
                String type = attachment.getKey().toLowerCase().endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
                helper.addAttachment(attachment.getKey(), new ByteArrayDataSource(attachment.getValue(), type));
            }
        }
        if (tags != null && !tags.isEmpty()) {
            message.setHeader("X-SES-MESSAGE-TAGS", tags.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        sender.send(message);
    }
}
