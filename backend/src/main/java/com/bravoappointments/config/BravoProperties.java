package com.bravoappointments.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "bravo")
public record BravoProperties(
        String publicBaseUrl,
        List<String> corsAllowedOrigins,
        Booking booking,
        Auth auth,
        Email email) {

    public record Booking(int slotStepMinutes, int minNoticeMinutes, int maxAdvanceDays) {}

    public record Auth(String issuer, String jwksUrl) {
        public boolean configured() {
            return StringUtils.hasText(issuer) && StringUtils.hasText(jwksUrl);
        }
    }

    public record Email(String resendApiKey, String from) {
        public boolean enabled() {
            return StringUtils.hasText(resendApiKey);
        }
    }
}
