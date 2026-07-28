FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

# exec 형식 ENTRYPOINT는 셸을 안 거쳐서 ${PROFILE}이 문자 그대로 전달되고, ARG는 런타임에
# 존재하지도 않는다. ENV로 옮겨야 빌드 시 값이 박히고 Spring이 환경변수로 읽는다.
# (-D 시스템 프로퍼티는 환경변수보다 우선순위가 높아, 거기에 ${PROFILE}을 두면 ENV가 무시된다.)
# 실행 시 -e SPRING_PROFILES_ACTIVE=dev 로 덮어쓸 수 있다.
ARG PROFILE=prod
ENV SPRING_PROFILES_ACTIVE=${PROFILE}

#COPY uploads
COPY --from=builder /app/build/libs/*.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]