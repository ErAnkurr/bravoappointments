package com.bravoappointments.notification;

public interface EmailSender {

    void send(String to, String subject, String html);
}
