package com.bravoappointments.notification;

import com.bravoappointments.config.BravoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {

    @Bean
    EmailSender emailSender(BravoProperties props) {
        BravoProperties.Email email = props.email();
        if (email != null && email.enabled()) {
            return new ResendEmailSender(email.resendApiKey(), email.from());
        }
        return new LoggingEmailSender();
    }
}
