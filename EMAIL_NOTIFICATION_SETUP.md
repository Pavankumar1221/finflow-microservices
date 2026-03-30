# FinFlow Email Notification Setup Guide

This guide explains how to use the newly added email notification feature in FinFlow.

## 1. What Has Been Implemented

Email sending is now built directly inside the existing services.

- `admin-service`
  - sends email when a loan is approved
  - sends email when a loan is rejected
- `document-service`
  - sends email when a document is verified
  - sends email when a document is rejected
- `auth-service`
  - now exposes a hidden internal endpoint so other services can fetch a user's name and email safely

No new microservice was created.

No new RabbitMQ queue was created for this feature.

This was intentionally kept simple for learning purposes.

## 2. How The Flow Works

### Loan Approved or Rejected

1. Admin calls:
   - `POST /admin/applications/{id}/decision`
2. `admin-service` saves the decision in the database
3. `admin-service` fetches the application details
4. `admin-service` fetches the applicant's email from `auth-service`
5. `admin-service` sends a direct email

### Document Verified or Rejected

1. Admin verifies or rejects a document
2. `document-service` updates the document status
3. `document-service` uses the uploader user id from the document record
4. `document-service` fetches that user's email from `auth-service`
5. `document-service` sends a direct email

## 3. Services Updated

The feature was added in these areas:

- `auth-service`
  - internal user lookup endpoint for notifications
- `admin-service`
  - mail dependency
  - mail configuration
  - loan decision email sender
- `document-service`
  - mail dependency
  - mail configuration
  - document status email sender

## 4. SMTP Setup Required

You must configure a real SMTP account before email can actually be sent.

The feature is controlled by:

- `FINFLOW_MAIL_ENABLED`

If mail is disabled, the services will skip email sending and continue the normal business flow.

## 5. Recommended Setup For Learning

The easiest option is Gmail with an App Password.

### Step 1. Enable 2-Step Verification in Gmail

Do this in your Google account security settings.

### Step 2. Generate an App Password

Create an App Password for mail usage.

You will use:

- your Gmail address as username
- the generated App Password as password

Do not use your normal Gmail login password.

## 6. Environment Variables To Set

Set these before starting `admin-service` and `document-service`.

```powershell
$env:FINFLOW_MAIL_ENABLED="true"
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-16-char-app-password"
$env:FINFLOW_MAIL_FROM="your-email@gmail.com"
```

If you use another provider, replace host and port accordingly.

## 7. Config Already Added In The Project

Mail placeholders were already added to:

- `config-repo/admin-service.yml`
- `config-repo/document-service.yml`
- `admin-service/src/main/resources/application.yml`
- `document-service/src/main/resources/application.yml`

These use environment variables so you do not need to hardcode credentials in source files.

## 8. How To Start After Setup

After setting the environment variables:

1. start or restart `config-server`
2. start or restart `eureka-server`
3. start or restart `auth-service`
4. start or restart `application-service`
5. start or restart `document-service`
6. start or restart `admin-service`
7. start or restart `api-gateway`

Restarting `admin-service` and `document-service` is mandatory for the new mail code.

## 9. How To Test Loan Approval Email

### Use This Endpoint

`POST /admin/applications/{id}/decision`

### Example Request

```json
{
  "decision": "APPROVED",
  "remarks": "All documents verified and loan approved",
  "approvedAmount": 200000.00,
  "approvedTenureMonths": 60,
  "interestRate": 8.50,
  "terms": "EMI"
}
```

### Expected Result

The applicant should receive an email containing:

- greeting with user name
- application number
- approved amount
- interest rate
- approved tenure
- terms
- remarks

## 10. How To Test Loan Rejection Email

### Example Request

```json
{
  "decision": "REJECTED",
  "remarks": "Income criteria not met"
}
```

### Expected Result

The applicant should receive a rejection email with:

- greeting
- application number
- rejection update
- remarks

## 11. How To Test Document Verification Email

Use the existing admin document endpoints:

- `PUT /admin/documents/{documentId}/verify`
- `PUT /admin/documents/{documentId}/reject`

### Verify Example

```json
{
  "remarks": "AADHAR verified successfully"
}
```

### Reject Example

```json
{
  "remarks": "AADHAR image is unclear"
}
```

### Expected Result

The user should receive an email with:

- greeting
- document type
- file name
- status
- remarks

## 12. Important Behavior Notes

- Email sending is direct inside the service
- Core DB updates happen first
- If email sending fails, the main business action still remains saved
- Failures are logged as warnings
- This is good for a learning project because it keeps the architecture simple

## 13. Where Email Is Triggered

### Loan Decision

Triggered from `admin-service` after the decision record is saved.

### Document Status

Triggered from `document-service` when status changes to:

- `VERIFIED`
- `REJECTED`

## 14. Troubleshooting

### Problem: No email received

Check:

- `FINFLOW_MAIL_ENABLED=true`
- SMTP username is correct
- SMTP password is the App Password, not your normal password
- service restarted after setting environment variables
- spam folder
- service logs for mail errors

### Problem: Service runs but skips email

Most likely:

- mail is disabled
- SMTP config is missing
- no `JavaMailSender` bean could be created

### Problem: Auth lookup fails

Check that:

- `auth-service` is running
- `eureka-server` is running
- services are registered correctly

## 15. Suggested Next Improvement

After you understand this simple version, the next upgrade could be:

- move email sending to RabbitMQ-based async notification handling

That would make the architecture more advanced, but for now the current direct-service implementation is the right level for a fresher learning project.
