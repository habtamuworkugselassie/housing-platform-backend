# Deploying Ethio Build Connect to a single droplet

Everything on one box: Postgres, Redis, the Spring backend, the nginx frontend and a
Caddy TLS terminator. Five containers, no Kafka (see the note in the compose file),
no external identity provider — the app signs its own JWTs.

**Sizing.** 4 GB RAM is the practical floor and it is tight *during the build*, because
Maven and Vite both want memory at once. Add swap before the first build (step 2) or
use an 8 GB droplet. Steady-state the stack sits comfortably under 3 GB.

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
# swap, so the first build does not get OOM-killed on a 4 GB box
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# docker
curl -fsSL https://get.docker.com | sh

# firewall: only ssh and web. Postgres and Redis are not published by the compose
# file, but a firewall means a future `ports:` typo cannot expose them either.
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
```

## 3. Clone both repositories as siblings

The frontend image is built from source, so the compose file reaches `../../housing-platform-frontend`. This layout is required:

```bash
mkdir -p /opt/ethiobuildconnect && cd /opt/ethiobuildconnect
git clone https://github.com/habtamuworkugselassie/housing-platform-backend.git
git clone https://github.com/habtamuworkugselassie/housing-platform-frontend.git
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

## 5. Build and start

```bash
docker compose -f deploy/docker-compose.prod.yml up -d --build    # 10-20 min first time
docker compose -f deploy/docker-compose.prod.yml logs -f backend
```

Flyway runs all 70 migrations against the empty database on first boot. Wait for
`Successfully applied` and then a Spring `Started` line before moving on.

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

```bash
cd /opt/ethiobuildconnect/housing-platform-backend && git pull
cd ../housing-platform-frontend && git pull
cd ../housing-platform-backend
docker compose -f deploy/docker-compose.prod.yml up -d --build
```

Flyway applies any new migrations on restart. Take a backup first (step 9) — a
migration is not reversible.

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
