# Flyway ran but new tables are missing on prod

## Most likely causes

### 1. **New migrations are not in the deployed artifact (most common)**

Flyway only runs migration files that are on the classpath. If the JAR/WAR deployed to production was built **before** you added V20, V21, V22 (or was built from a branch that doesn’t have them), then:

- On startup Flyway runs, sees `flyway_schema_history` with e.g. V1–V19.
- It finds **no pending** migrations (V20, V21, V22 are not in the JAR).
- So it does nothing and reports “already up to date”.
- The “new” tables (e.g. `exhibition_interest`, `media_attachments`, etc.) are never created because those migrations were never part of the prod deployment.

**Check:**

- On the prod server, open the deployed JAR and confirm the new scripts are there:
  ```bash
  jar tf your-app.jar | grep db/migration
  ```
  You should see at least:
  - `db/migration/V20__Create_exhibition_interest.sql`
  - `db/migration/V21__Exhibition_interest_phone_and_organization.sql`
  - `db/migration/V22__Insert_sample_realestates.sql`
  (and any other new ones you expect.)
- Rebuild the app from the branch/commit that contains these migrations and redeploy so the same JAR that runs in prod includes them.

---

### 2. **Different schema**

Tables might exist in another schema (e.g. `public` vs the one Flyway uses).

**Check on prod DB:**

```sql
-- Which schema does Flyway use?
SELECT DISTINCT installed_rank, version, description, script, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- Do the new tables exist in public?
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_name IN ('exhibition_interest', 'media_attachments', 'password_reset_tokens')
ORDER BY table_schema, table_name;
```

If `flyway_schema_history` has V20/V21/V22 with `success = true` but the tables are not in the schema your app uses, fix the schema (e.g. set `spring.flyway.schemas` or the datasource default schema) and/or move the tables as needed.

---

### 3. **Migration failed but history was not updated (rare)**

If a migration failed after committing something (e.g. with `executeInTransaction: false`), you could have partial state. Usually with default settings the whole migration rolls back and the version is **not** inserted as successful.

**Check:**

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
```

If the latest versions show `success = false`, that migration failed and may have left the DB in a bad state; you’ll need to fix data/objects and then repair or re-run (see Flyway docs).

---

### 4. **Wrong database (URL / environment)**

Confirm the app in production is actually pointing at the prod DB (e.g. `SPRING_DATASOURCE_URL` or `DATABASE_URL`). If it points at staging or another DB, Flyway will run there and prod will never get the new migrations.

---

## What to do

1. **Confirm prod JAR has the new migrations** (see §1). If they are missing, add them to the build and redeploy.
2. **Check `flyway_schema_history` and table list** on the real prod DB (see §2 and §3).
3. **If V20+ are in history and successful** but tables are still missing, then either:
   - Tables are in another schema (fix schema/config), or
   - You’re looking at a different DB than the one the app uses (fix URL/env).
4. **If V20+ are not in history** and the JAR does contain them, run the app once against prod (or run Flyway manually against prod) so it applies pending migrations. Ensure the process has network/DB access and that you’re not running against a read replica.

---

## Optional: verify from the app (actuator)

If you have Flyway actuator enabled:

```yaml
management:
  endpoint:
    flyway:
      enabled: true
  endpoints:
    web:
      exposure:
        include: flyway
```

Then call `/actuator/flyway` (with auth if required) to see which migrations are applied. That confirms what the **running app** thinks has run, for the DB it’s connected to.
