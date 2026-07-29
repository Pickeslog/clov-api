package com.korit.clovapi.global.mail;

/**
 * 메일 발송 추상화.
 *
 * <p>구현체를 갈아끼울 수 있게 인터페이스로 둔 이유는 셋이다.
 * <ol>
 *   <li>로컬 개발과 <b>Testcontainers 통합테스트</b>가 실제 메일을 발송하는 사고를 원천 차단한다.</li>
 *   <li>SMTP 계정이 없어도 기능을 끝까지 구현·검증할 수 있어 착수가 막히지 않는다.</li>
 *   <li>나중에 외부 발송 서비스로 옮겨도 도메인 로직은 바뀌지 않는다.</li>
 * </ol>
 *
 * <p>선택은 {@code app.mail.mode}가 한다 — {@code log}(기본) 또는 {@code smtp}. {@link MailConfig} 참고.
 */
public interface MailSender {

    /**
     * 메일 한 통을 보낸다. <b>발송 실패가 호출자의 응답을 바꾸면 안 된다</b> —
     * 계약 §4-4의 "항상 200"이 깨지면 응답으로 계정 존재 여부가 샌다.
     */
    void send(String to, String subject, String body);
}
