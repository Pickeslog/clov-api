# Clov 배포 런북 — GCP VM 단일 오리진 (clovlabcalss.store)

> 구조: nginx 하나가 **프론트 정적(dist/)** + **`/api`·OAuth 백엔드 프록시(:8080)** 를 서빙. DB = **VM 로컬 MySQL**. HTTPS = **certbot**.
> 표기: 🖥️=VM(SSH)에서, 💻=로컬(개발 PC)에서.
>
> 관련 파일(이 폴더): `nginx-clovlabcalss.conf` · `clov-api.service` · `clov-api.env.example`.

---

## 0. 전제
- 도메인 `clovlabcalss.store` A레코드 → VM 공개 IP (완료됨).
- VM = Ubuntu/Debian 계열, nginx 설치·기동 중(기본 페이지 확인됨).
- OAuth 앱(Google/Naver/Kakao)은 dev 것 재사용 가능 — **콘솔에 프로덕션 redirect URI만 추가**(7단계).

## 1. 🖥️ VM 패키지
```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless mysql-server
# certbot (HTTPS용)
sudo apt install -y certbot python3-certbot-nginx
java -version   # 21 확인
```

## 2. 🖥️ DB — MySQL 생성 + 스키마
```bash
sudo mysql
```
```sql
CREATE DATABASE clov CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'clov'@'localhost' IDENTIFIED BY '<강한_DB_비밀번호>';
GRANT ALL PRIVILEGES ON clov.* TO 'clov'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```
스키마 적용(**신규 DB라 마이그레이션 불필요** — `schema.sql`이 이미 최신 = 초대 `UNIQUE(room_id)` 포함):
```bash
# clov-api/src/test/resources/schema.sql 를 VM으로 복사한 뒤:
mysql -u clov -p clov < schema.sql
```

## 3. 백엔드 — 빌드 → jar → systemd
```bash
# 💻 로컬에서 jar 빌드 (또는 VM에서 소스 clone 후 빌드)
./gradlew bootJar          # build/libs/clov-api-*.jar 생성
```
```bash
# 🖥️ VM: 배치 디렉터리 + 사용자
sudo useradd -r -s /usr/sbin/nologin clov
sudo mkdir -p /opt/clov-api
# jar 업로드 후:
sudo mv clov-api-*.jar /opt/clov-api/clov-api.jar
```
**시크릿 파일** 🖥️ `/opt/clov-api/application-secret.yaml` 생성 — `application-secret.example.yaml`을 채운다:
- `spring.datasource.url` = `jdbc:mysql://localhost:3306/clov?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8`
- `spring.datasource.username/password` = 위 clov 계정
- OAuth `client-id/secret` (3사), `jwt.secret`(강한 base64), R2 `app.storage.*` (dev와 동일 clov-media 가능)

**배포 env** 🖥️ `/opt/clov-api/clov-api.env` — `clov-api.env.example` 복사(도메인 그대로면 수정 없음).

**서비스 등록**:
```bash
sudo cp clov-api.service /etc/systemd/system/clov-api.service
sudo chown -R clov:clov /opt/clov-api
sudo systemctl daemon-reload
sudo systemctl enable --now clov-api
sudo systemctl status clov-api        # active(running) 확인
sudo journalctl -u clov-api -f        # 부팅 로그(에러 없나)
curl -s localhost:8080/api/v1/rooms   # 401 UNAUTHORIZED = 정상 기동
```

## 4. 프론트 — 빌드 → dist → nginx 루트
```bash
# 💻 로컬(clov-web): 프로덕션 API 베이스로 빌드
VITE_API_BASE_URL=https://clovlabcalss.store/api/v1 npm run build
# → dist/ 생성. dist/ 전체를 VM으로 업로드
```
```bash
# 🖥️ VM
sudo mkdir -p /var/www/clov
# 업로드한 dist/* 를 /var/www/clov/ 로 배치
sudo cp -r dist/* /var/www/clov/
```

## 5. 🖥️ nginx 설정
```bash
sudo cp nginx-clovlabcalss.conf /etc/nginx/sites-available/clov
sudo ln -sf /etc/nginx/sites-available/clov /etc/nginx/sites-enabled/clov
sudo rm -f /etc/nginx/sites-enabled/default   # 기본 페이지 제거
sudo nginx -t && sudo systemctl reload nginx
```
→ 이 시점에 `http://clovlabcalss.store` 에서 Clov 앱(로그인 화면)이 떠야 함.

## 6. 🖥️ HTTPS — certbot
```bash
sudo certbot --nginx -d clovlabcalss.store -d www.clovlabcalss.store
# 이메일·약관 동의, "Redirect HTTP→HTTPS" 선택. 자동 갱신 타이머 등록됨.
```
→ `https://clovlabcalss.store` 접속 확인.

## 7. OAuth 콘솔 — 프로덕션 redirect URI 추가 (각 provider 콘솔에서)
기존 dev 리다이렉트에 **추가**(dev localhost는 유지):
- **Google**: 승인된 리디렉션 URI → `https://clovlabcalss.store/login/oauth2/code/google`
- **Naver**: Callback URL → `https://clovlabcalss.store/login/oauth2/code/naver`
- **Kakao**: Redirect URI → `https://clovlabcalss.store/login/oauth2/code/kakao`
  - 카카오는 플랫폼 사이트 도메인에 `https://clovlabcalss.store` 도 등록.

## 8. 프로덕션 스모크 테스트
- `https://clovlabcalss.store` → 로그인 화면(라이트 종이·Outfit).
- 이메일 회원가입 → 자동 로그인 → 방 목록.
- 방 생성 → 대시보드 진입.
- 소셜 로그인 3사 각각(콘솔 등록 후).
- 초대 코드 생성/재발급(1행 회전)·2계정 입장.
- 이미지 업로드(추억/프로필) — R2 설정 시.

## 9. 이후 재배포 / 롤백
- **백엔드 갱신**: 새 jar 업로드 → `sudo systemctl restart clov-api`.
- **프론트 갱신**: 재빌드 → `/var/www/clov` 교체 → (nginx reload 불필요, 정적).
- **DB 스키마 변경**: 마이그레이션 SQL 수동 적용(이 프로젝트는 Flyway 없음).
- **롤백**: 백엔드는 이전 jar로 교체 후 restart, 프론트는 이전 dist로 교체.

---

## ⚠️ 체크포인트
- **application-secret.yaml·clov-api.env 는 커밋 금지**(시크릿). VM에만.
- **CORS/redirect env**: 이 런북은 도메인 `clovlabcalss.store` 기준. 도메인 바뀌면 `clov-api.env` 3개 값 + 프론트 `VITE_API_BASE_URL` + OAuth 콘솔 전부 교체.
- **방화벽**: GCP 방화벽에서 80·443 인그레스 허용 확인(8080은 외부 개방 불필요 — nginx만 접근).
- **JWT secret**: dev와 다른 강한 값 권장(`openssl rand -base64 48`).
