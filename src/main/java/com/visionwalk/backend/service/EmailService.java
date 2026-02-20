package com.visionwalk.backend.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmergencyAlert(String to, String userName, double lat, double lng) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("🚨 EMERGENCY: " + userName + " needs help!");
        
        String mapsLink = "http://maps.google.com/maps?q=" + lat + "," + lng;
        
        message.setText("VisionWalk Alert System\n\n" +
                        "User: " + userName + " has triggered an SOS.\n" +
                        "Location: " + mapsLink + "\n\n" +
                        "Please verify their safety immediately.");
                        
        mailSender.send(message);
        System.out.println("Alert email sent to " + to);
    }
}