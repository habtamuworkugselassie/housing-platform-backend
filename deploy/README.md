# Deploying Ethio Build Connect to a single droplet

Everything on one box: Postgres, Redis, the Spring backend, the nginx frontend and a
Caddy TLS terminator. Five containers, no Kafka (see the note in the compose file),
no external identity provider — the app signs its own JWTs.

**Images are built by GitHub Actions**, not on the droplet. `.github/workflows/deploy-*.yml`
in each repo build, push to GHCR, copy the image onto the droplet and restart the stack.
The droplet therefore never runs Maven or Vite, and 4 GB is comfortable.

---

## 1. DNS first

Both names must resolve to the droplet **before** the first start, or Caddy's
certificate request fails and you get a TLS error instead of a site.

| Record | Name | Value |
|---|---|---|
| A | `@` | the droplet's IPv4 |
| A | `www` | the droplet's IPv4 |

Confirm from your laptop, not the droplet: `dig +short ethiobuildconnect.et`

## 2. Prepare the droplet

```bash
# docker
curl -fsSL https://get.docker.com | sh

# firewall: only ssh and web. Postgres and Redis are not published by the compose
# file, but a firewall means a future `ports:` typo cannot expose them either.
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
```

## 3. Clone the backend repository

Only this one is needed on the droplet: it holds `deploy/docker-compose.prod.yml`, which
defines the whole stack. The frontend arrives as a prebuilt image. This path is the
default both workflows expect — override it with an Actions **variable** `DEPLOY_DIR`
if you put it elsewhere.

```bash
mkdir -p /opt/ethiobuildconnect && cd /opt/ethiobuildconnect
git clone https://github.com/habtamuworkugselassie/housing-platform-backend.git
```

## 4. Fill in the secrets

```bash
cd /opt/ethiobuildconnect/housing-platform-backend
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
openssl rand -base64 32   # -> POSTGRES_PASSWORD
openssl rand -base64 48   # -> JWT_SECRET  (min 32 chars, enforced at startup)
nano deploy/.env
```

Every `REQUIRED` line in that file has no usable default. `deploy/.env` is gitignored —
keep it that way.

## 5. First start

The app images do not exist on the droplet yet — nothing has deployed. Bring up the
infrastructure first, then let a deploy supply the rest:

```bash
# postgres, redis and caddy pull from public registries and start immediately
docker compose -f deploy/docker-compose.prod.yml up -d postgres redis caddy
```

Then run **Build and Deploy Backend** and **Build and Deploy Frontend** from the Actions
tab of each repository (or push to `main`). Each one builds its image, copies it to the
droplet, and starts its service.

Watch the first backend deploy — Flyway runs all 70 migrations against the empty
database on first boot:

```bash
docker compose -f deploy/docker-compose.prod.yml logs -f backend
```

### GitHub secrets the deploys need

Set these in **each** repository under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `DO_HOST` | the droplet's IP — **update this when moving to a new instance** |
| `DO_SSH_USER` | the deploy user (see the note below about not using root) |
| `DO_SSH_KEY` | that user's **private** key, whole file including the header line |

Frontend repo also needs `BACKEND_URL` = `/api/v1` (a relative path: the browser calls
the same origin and nginx proxies it, so this does not change per environment).

Backend repo also wants `FRONTEND_BASE_URL`, `PUBLIC_API_BASE_URL`, the `CHAPA_*`
pair and the support-chat keys — all listed in `deploy/.env.example` with what each
one is for.

The SSH key gives GitHub Actions shell on the droplet. Prefer a dedicated `deploy`
user in the `docker` group over `root`, so a leaked key is not immediately root.

## 6. Remove the sample data — before announcing the site

`V22__Insert_sample_realestates.sql` seeds 35 sample organizations. On a fresh
production database those are live, publicly visible and indexable: the site would
advertise companies that do not exist.

```bash
docker compose -f deploy/docker-compose.prod.yml exec postgres \
  psql -U housing_platform -d housing_platform \
  -c "SELECT id, name, status FROM organizations ORDER BY created_at LIMIT 50;"
```

Delete or suspend the ones that are not real. Suspending is safer than deleting —
the sitemap and marketplace both filter to `APPROVED`, so a suspended row disappears
from the public site without breaking foreign keys:

```sql
UPDATE organizations SET status = 'SUSPENDED' WHERE name IN ('...', '...');
```

## 7. Super admin

`V52__Bootstrap_super_admin.sql` creates `superadmin@ethiobuildconnect.et`. Sign in
once and change the password immediately — the bootstrap credential is in the
repository.

## 8. Verify

```bash
curl -sI https://ethiobuildconnect.et/ | head -1                      # 200, valid cert
curl -sI http://ethiobuildconnect.et/ | grep -i location              # -> https
curl -sI https://www.ethiobuildconnect.et/ | grep -i location         # -> apex
curl -s  https://ethiobuildconnect.et/sitemap.xml | grep -c '<loc>'   # > 0
curl -sI https://ethiobuildconnect.et/properties | grep -i '^link'    # canonical = /properties
curl -s  https://ethiobuildconnect.et/actuator/health                 # {"status":"UP"}
```

The brochure redirects added with the 2027 rename are worth one check too:

```bash
curl -sI https://ethiobuildconnect.et/docs/ethio-build-connect-expo-2026-brochure.html \
  | grep -i location          # -> .../expo-2027-brochure.html
```

## 9. Back up the database — do this on day one

The previous database was lost. Nothing in this stack prevents that happening again.

```bash
mkdir -p /var/backups/ethiobuildconnect
cat > /etc/cron.daily/ethiobuildconnect-db <<'EOF'
#!/bin/sh
cd /opt/ethiobuildconnect/housing-platform-backend
docker compose -f deploy/docker-compose.prod.yml exec -T postgres \
  pg_dump -U housing_platform housing_platform \
  | gzip > /var/backups/ethiobuildconnect/db-$(date +%F).sql.gz
find /var/backups/ethiobuildconnect -name 'db-*.sql.gz' -mtime +14 -delete
EOF
chmod +x /etc/cron.daily/ethiobuildconnect-db
/etc/cron.daily/ethiobuildconnect-db && ls -lh /var/backups/ethiobuildconnect
```

A backup on the same droplet does not survive losing the droplet. Copy these off the
box — DigitalOcean Spaces, or `scp` on a schedule — and enable droplet backups in the
DigitalOcean control panel.

**Uploaded media is not in the database.** Listing photos live in the `uploads` volume.
Back that up too:

```bash
docker run --rm -v ethiobuildconnect_uploads:/u -v /var/backups/ethiobuildconnect:/b \
  alpine tar czf /b/uploads-$(date +%F).tar.gz -C /u .
```

## 10. Deploying a change later

Push to `main` in either repository. The workflow builds the image, copies it over,
restarts that service and waits on a health check — rolling back to the previous image
if the new one does not come up.

Nothing needs doing on the droplet, including for changes to the stack itself: the
deploy runs `git pull` in this checkout first, so an edit to
`deploy/docker-compose.prod.yml` ships with the commit that makes it.

Flyway applies new migrations on restart. **Take a backup first (step 9)** — the health
gate rolls back the image, but it cannot roll back a migration.

---

## Notes

**TLS terminates at Caddy**, and the frontend container listens on `:80` only. That is
why `http -> https` is not in `nginx.conf`: `$scheme` there is always `http`, so
redirecting on it would loop. Caddy redirects by default and passes
`X-Forwarded-Proto` through.

**Kafka and Zookeeper are deliberately absent.** `application.yml` configures them,
but nothing in `src/main/java` references Kafka — running them would cost roughly
1.5 GB to carry an unused dependency. The dev `docker-compose.yml` at the repo root
still starts them; it is for local development, not this stack.

**LiveKit is separate.** Live broadcasting is feature-flagged off and has its own
stack in `infra/livekit/`. Bring it up only when the flag is turned on; it needs
more RAM than the rest of this list combined.
