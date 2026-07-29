package com.korit.clovapi.global.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 발송기 선택 — {@code app.mail.mode}.
 *
 * <table>
 *   <tr><td>{@code log}</td><td><b>기본값.</b> 콘솔에만 출력. SMTP 설정이 없어도 부팅된다</td></tr>
 *   <tr><td>{@code smtp}</td><td>실제 발송. {@code spring.mail.*}(host·username·password)가 있어야 한다</td></tr>
 * </table>
 *
 * <p><b>기본값을 {@code log}로 둔 것은 의도적이다.</b> 배포 서버에 메일 설정이 없는 상태에서
 * 이 변경이 머지돼도 부팅이 깨지지 않는다. SMTP 계정이 준비되면 {@code clov-api.env}에
 * {@code APP_MAIL_MODE=smtp}를 넣고 시크릿에 {@code spring.mail.*}를 추가하면 되고,
 * 코드는 다시 건드리지 않는다.
 */
@Configuration
@EnableAsync
public class MailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "smtp")
    public MailSender smtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${app.mail.from}") String from
    ) {
        return new SmtpMailSender(javaMailSender, from);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "log", matchIfMissing = true)
    public MailSender loggingMailSender() {
        return new LoggingMailSender();
    }
}
