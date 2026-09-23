package com.memme.service.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailSender {

    private static final String SUBJECT = "맴매 비밀번호 재설정 안내";

    private final JavaMailSender javaMailSender;

    public PasswordResetMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    public void send(String recipientEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText("비밀번호를 재설정하려면 아래 링크를 열어주세요.\n\n" + resetLink);

        javaMailSender.send(message);
    }
}
