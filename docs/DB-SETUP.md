# Local DB Setup

## Source of truth

The schema is defined in the shared DB SSOT:
[`05-db-unified-final.md`](../../web-design-repository/api-spec/05-db-unified-final.md).

The shared development database already has the approved schema. Do not run the
DDL again against it and never run `DROP DATABASE`, `DROP TABLE`, or `TRUNCATE`.
Schema changes require an approved SSOT change first.

## Local configuration

Copy the committed template to a local-only file:

```powershell
Copy-Item src/main/resources/application-secret.example.yaml src/main/resources/application-secret.yaml
```

Fill in only the database host, database name, username, and password obtained
from the team. `application-secret.yaml` is ignored by Git; do not add, commit,
or share it.

## Verification

Start with the application context check:

```powershell
.\gradlew.bat test
```

For a non-destructive schema check, connect interactively and run:

```sql
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE();
```

The approved shared schema contains 19 tables. Mapper XML files belong under
`src/main/resources/mapper/`, and domain entities belong under
`com.korit.clovapi.domain`.
