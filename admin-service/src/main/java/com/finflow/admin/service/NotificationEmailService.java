package com.finflow.admin.service;

import com.finflow.admin.entity.Decision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public void sendLoanDecisionEmailQuietly(Map<String, Object> user, Map<String, Object> application,
                                             Decision decision, String remarks) {
        try {
            sendLoanDecisionEmail(user, application, decision, remarks);
        } catch (Exception ex) {
            log.warn("Loan decision email could not be sent for application {}: {}",
                    decision != null ? decision.getApplicationId() : null, ex.getMessage());
        }
    }

    private void sendLoanDecisionEmail(Map<String, Object> user, Map<String, Object> application,
                                       Decision decision, String remarks) {
        if (!mailEnabled) {
            log.info("Mail feature is disabled. Skipping loan decision email for application {}",
                    decision.getApplicationId());
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Skipping loan decision email for application {}",
                    decision.getApplicationId());
            return;
        }

        String recipient = asString(user.get("email"));
        if (recipient == null || recipient.isBlank()) {
            log.warn("No recipient email found for application {}", decision.getApplicationId());
            return;
        }

        String userName = defaultString(asString(user.get("fullName")), "Customer");
        String applicationNumber = defaultString(asString(application.get("applicationNumber")),
                String.valueOf(decision.getApplicationId()));
        String subject = decision.getDecisionStatus() == Decision.DecisionStatus.APPROVED
                ? "FinFlow Loan Approved - " + applicationNumber
                : "FinFlow Loan Decision Update - " + applicationNumber;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(buildBody(userName, applicationNumber, decision, remarks));
        mailSender.send(message);

        log.info("Loan decision email sent to {} for application {}", recipient, decision.getApplicationId());
    }

    private String buildBody(String userName, String applicationNumber, Decision decision, String remarks) {
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(userName).append(",\n\n");

        if (decision.getDecisionStatus() == Decision.DecisionStatus.APPROVED) {
            body.append("Congratulations. Your loan application has been approved.\n\n");
            body.append("Application Number: ").append(applicationNumber).append("\n");
            body.append("Approved Amount: ").append(formatMoney(decision.getApprovedAmount())).append("\n");
            body.append("Interest Rate: ").append(formatRate(decision.getInterestRate())).append("\n");
            body.append("Approved Tenure: ")
                    .append(decision.getApprovedTenureMonths() != null ? decision.getApprovedTenureMonths() + " months" : "N/A")
                    .append("\n");
            body.append("Terms: ").append(defaultString(decision.getTerms(), "N/A")).append("\n");
            body.append("Remarks: ").append(defaultString(remarks, "Approved successfully")).append("\n\n");
        } else {
            body.append("We would like to inform you that your loan application was not approved.\n\n");
            body.append("Application Number: ").append(applicationNumber).append("\n");
            body.append("Remarks: ").append(defaultString(remarks, "Please contact support for more details.")).append("\n\n");
        }

        body.append("Thank you for choosing FinFlow.\n");
        body.append("Regards,\n");
        body.append("FinFlow Team");
        return body.toString();
    }

    private String formatMoney(BigDecimal value) {
        return value != null ? "Rs. " + value.toPlainString() : "N/A";
    }

    private String formatRate(BigDecimal value) {
        return value != null ? value.toPlainString() + "%" : "N/A";
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
