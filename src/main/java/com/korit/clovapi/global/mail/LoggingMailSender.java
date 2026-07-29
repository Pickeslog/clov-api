package com.korit.clovapi.global.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 발송하지 않고 로그로만 남기는 구현체(기본값).
 *
 * <p>SMTP 설정이 없는 로컬·CI·아직 메일 계정을 붙이지 않은 배포에서 쓴다.
 * 재설정 링크가 로그에 그대로 찍히므로 <b>운영에서 기본값으로 두면 안 된다</b> —
 * 로그 열람 권한이 있는 사람이 남의 비밀번호를 재설정할 수 있다.
 */
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[MAIL:log] to={} subject={}\n{}", to, subject, body);
    }
}
