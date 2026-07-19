# Clov API — Agent Guide

## Start here

1. Read this file.
2. Read `docs/API-CONTRACT.md`; its linked SSOT is the only API contract.
3. For a task touching UI behavior or data, also read the relevant document in `../web-design-repository/`.

## Commands

- Test: `./gradlew test` (Windows: `./gradlew.bat test`)
- Run: `./gradlew bootRun` (Windows: `./gradlew.bat bootRun`)
- Build: `./gradlew build` (Windows: `./gradlew.bat build`)

Before finishing, run the relevant tests. If Java 21 is unavailable, state that verification blocker plainly.

## Stack and structure

- Java 21, Spring Boot 4.0.x, Spring Security 6, MyBatis 4.0.1, MySQL 8, Gradle
- Do not add JPA, `@Entity`, Spring Data repositories, or ORM-generated SQL.
- Organize new code by domain: `domain/<domain>/{controller,service,mapper,dto,entity}`.
- Put shared response, security, and exception code under `global/`.
- Keep controllers thin; put transactions and business rules in services.
- Mapper interfaces and mapper XML `namespace`/statement `id` must match exactly.
- Use `#{}` for SQL parameters. `${}` is prohibited unless a reviewed, fixed allowlist makes it unavoidable.

## Contract and service rules

- Base path is `/api/v1`.
- All responses use `{success,data}` or `{success,error:{code,message}}`.
- JSON IDs are strings; database IDs are `BIGINT`.
- There are no room owners, admins, or roles. All active room members are equal.
- Authorization is limited to active room membership and writer/sender ownership where the contract requires it.
- Preserve records: leave is `LEFT`, withdrawal is anonymization, and memory deletion is soft delete.
- Do not change the API contract, DB schema, or DDL without a leader-approved issue/PR.

## Security

- Never read, print, commit, or modify real secret values.
- `application-secret.yaml` and `.env*` are local-only. Commit placeholders only, such as `application-secret.example.yaml`.
- Do not put access tokens or refresh tokens in URLs, logs, exception messages, or fixtures.
- Passwords use BCrypt; password fields never appear in response DTOs.

## Collaboration

- One issue = one branch = one focused PR. Do not work directly on `main`.
- Branches: `feat/<issue>-<topic>`, `fix/<issue>-<topic>`, or `chore/<topic>`.
- Commits and PR titles use Conventional Commits, for example `feat: implement login API (#6)`.
- Keep unrelated local changes out of the PR.
- New dependencies, contract changes, and schema changes require team confirmation first.

## Completion report

Report changed files, verification performed, and any unverified or blocked item in three short bullets.
