package com.korit.clovapi.global.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * 실제 SMTP 발송({@code app.mail.mode=smtp}).
 *
 * <p><b>비동기다.</b> 동기로 보내면 SMTP 지연이 그대로 API 응답 시간이 되고, 더 중요하게는
 * <b>응답 시간 차이로 계정 존재 여부가 샌다</b>(계정이 있으면 발송하느라 느리고 없으면 즉시 반환).
 * 계약 §4-4가 "항상 200"으로 본문을 통일해도 타이밍으로 새면 의미가 없다.
 *
 * <p>같은 이유로 발송 실패를 삼킨다. 여기서 예외를 올리면 이미 응답이 나간 뒤이기도 하고,
 * 동기 구간이었다면 실패 여부가 곧 계정 존재 여부가 된다.
 */
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final String from;

    public SmtpMailSender(JavaMailSender javaMailSender, String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Async
    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            javaMailSender.send(message);
        } catch (MailException exception) {
            // 수신자 주소는 남기지 않는다(계정 존재 여부가 로그로 새지 않게).
            log.warn("[MAIL:smtp] 발송 실패 subject={} cause={}", subject, exception.getClass().getSimpleName());
        }
    }
}
