# Google Mail verification setup

SwiftRoute sends verification links through `notification-service` using Gmail SMTP.

1. Enable 2-Step Verification on the Google account used as the sender.
2. Create a Google App Password for mail delivery.
3. Add these values to the root `.env` file. Do not commit this file.

```dotenv
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_FROM=your-account@gmail.com
SMTP_USERNAME=your-account@gmail.com
SMTP_PASSWORD=your-16-character-app-password
SMTP_AUTH=true
SMTP_STARTTLS=true
WEB_APP_URL=http://localhost:3000
```

Restart `auth-service` and `notification-service` after changing the environment. Registration creates a one-time token, publishes an outbox event, and Notification Service sends the link. The link expires after 24 hours. Public resend is rate-limited by the existing token service and does not disclose whether an email account exists.
