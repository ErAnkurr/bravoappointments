package com.bravoappointments.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no Resend key is configured: emails are written to the log instead of sent. */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String html) {
        log.info("[email not sent: RESEND_API_KEY is blank] to={} subject=\"{}\"\n{}", to, subject, html);
    }
}
