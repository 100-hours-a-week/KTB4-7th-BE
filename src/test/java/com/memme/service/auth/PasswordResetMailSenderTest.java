package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private PasswordResetMailSender passwordResetMailSender;

    @Test
    void 수신_이메일과_재설정_링크로_안내_메일을_발송한다() {
        String recipientEmail = "owner@example.com";
        String resetLink = "http://localhost:5173/password-reset?token=reset-token";

        passwordResetMailSender.send(recipientEmail, resetLink);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();

        assertThat(message.getTo()).containsExactly(recipientEmail);
        assertThat(message.getSubject()).isEqualTo("맴매 비밀번호 재설정 안내");
        assertThat(message.getText()).contains(resetLink);
    }
}
