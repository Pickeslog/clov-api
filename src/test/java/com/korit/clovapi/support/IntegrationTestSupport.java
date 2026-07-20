package com.korit.clovapi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합테스트 공통 베이스. 실제 공유 DB(application-secret.yaml) 대신
 * Testcontainers MySQL을 띄우고 {@code schema.sql}로 초기화한다.
 *
 * <p><b>싱글턴 컨테이너 패턴</b>: static 초기화 블록에서 컨테이너를 1회 기동하고
 * JVM 종료까지 유지한다. 여러 테스트 클래스가 공유할 때 {@code @Testcontainers}의
 * 클래스별 {@code AfterAll} stop이 다음 클래스의 DB 연결을 끊는 문제를 피한다
 * (컨테이너 정리는 Testcontainers Ryuk가 담당). {@code @ServiceConnection}이
 * datasource를 동적으로 주입하므로 URL 설정은 불필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withInitScript("schema.sql");

    static {
        MYSQL.start();
    }
}
