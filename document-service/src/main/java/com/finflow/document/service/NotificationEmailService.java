package com.finflow.document.service;

import com.finflow.document.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${finflow.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${finflow.mail.from:${spring.mail.username:no-reply@finflow.local}}")
    private String fromAddress;

    public void sendDocumentStatusEmailQuietly(Map<String, Object> user, Document document, String remarks) {
        try {
            sendDocumentStatusEmail(user, document, remarks);
        } catch (Exception ex) {
            log.warn("Document status email could not be sent for document {}: {}",
                    document != null ? document.getId() : null, ex.getMessage());
        }
    }

    private void sendDocumentStatusEmail(Map<String, Object> user, Document document, String remarks) {
        if (!mailEnabled) {
            log.info("Mail feature is disabled. Skipping document email for document {}", document.getId());
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Skipping document email for document {}", document.getId());
            return;
        }

        String recipient = asString(user.get("email"));
        if (recipient == null || recipient.isBlank()) {
            log.warn("No recipient email found for document {}", document.getId());
            return;
        }

        String userName = defaultString(asString(user.get("fullName")), "Customer");
        String subject = "FinFlow Document Update - " + defaultString(document.getDocumentType(), "Document");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(buildBody(userName, document, remarks));
        mailSender.send(message);

        log.info("Document status email sent to {} for document {}", recipient, document.getId());
    }

    private String buildBody(String userName, Document document, String remarks) {
        String status = document.getVerificationStatus() != null
                ? document.getVerificationStatus().name()
                : "UPDATED";

        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(userName).append(",\n\n");

        if (document.getVerificationStatus() == Document.VerificationStatus.VERIFIED) {
            body.append("Your document has been verified successfully.\n\n");
        } else if (document.getVerificationStatus() == Document.VerificationStatus.REJECTED) {
            body.append("Your document was reviewed but could not be approved.\n\n");
        } else {
            body.append("There is an update on your submitted document.\n\n");
        }

        body.append("Document Type: ").append(defaultString(document.getDocumentType(), "N/A")).append("\n");
        body.append("File Name: ").append(defaultString(document.getFileName(), "N/A")).append("\n");
        body.append("Status: ").append(status).append("\n");
        body.append("Remarks: ").append(defaultString(remarks, "No additional remarks")).append("\n\n");
        body.append("Regards,\n");
        body.append("FinFlow Team");
        return body.toString();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
