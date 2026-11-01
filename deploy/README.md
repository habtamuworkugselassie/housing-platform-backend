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

On a fresh droplet, as root. A stock image has neither Docker nor git.

```bash
apt update && apt install -y git curl

# Docker, plus the compose v2 plugin (`docker compose`, not `docker-compose`)
curl -fsSL https://get.docker.com | sh
docker --version && docker compose version     # both must print a version

# Firewall: only ssh and web. Postgres and Redis publish no ports in the compose
# file, but a firewall means a future `ports:` typo cannot expose them either.
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
```

### A deploy user, not root

The GitHub deploy key gives Actions a shell on this box. Anyone who can push to
`main` — or who compromises the repository — inherits it, so it should not be root.
Membership of `docker` is enough to run the whole pipeline.

```bash
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy

# the key GitHub will authenticate with (public half only)
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh
nano /home/deploy/.ssh/authorized_keys        # paste ebc_deploy.pub
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

`DO_SSH_USER` is then `deploy`, and `DO_SSH_KEY` is the matching **private** key —
the file without `.pub`, pasted whole including its BEGIN and END lines.

Check it from your own machine before relying on it:

```bash
ssh -i ~/.ssh/ebc_deploy deploy@<droplet-ip> 'docker ps'
```

If that prints an empty container table, the Action will work.

## 3. Clone the backend repository

Only this one is needed on the droplet: it holds `deploy/docker-compose.prod.yml`,
which defines the whole stack. The frontend arrives as a prebuilt image.

Clone it **as the deploy user** — the workflow runs `git pull` here as that user, and
a root-owned checkout makes that fail. This path is the default both workflows expect;
override it with an Actions **variable** `DEPLOY_DIR` if you put it elsewhere.

The repository is **private**, so an anonymous `https://` clone prompts for a GitHub
username — and fails with `could not read Username ... No such device or address`
when there is no terminal to answer it. The workflow also runs `git pull` on every
deploy, so the droplet needs read access that persists. Give it a read-only
**deploy key** rather than anyone's personal credentials:

```bash
# 1. A key pair the deploy user will present to GitHub (separate from ebc_deploy,
#    which is the key GitHub Actions presents to the droplet).
sudo -u deploy ssh-keygen -t ed25519 -N "" -C ebc-droplet-readonly \
  -f /home/deploy/.ssh/github_ro
cat /home/deploy/.ssh/github_ro.pub
```

Add that `.pub` line at
<https://github.com/habtamuworkugselassie/housing-platform-backend/settings/keys>
→ *Add deploy key*. Leave **Allow write access** unchecked: the droplet only pulls.

```bash
# 2. Tell ssh to use it for github.com, trust GitHub's host key, and clone over SSH.
sudo -u deploy sh -c 'printf "Host github.com\n  IdentityFile ~/.ssh/github_ro\n  IdentitiesOnly yes\n" >> ~/.ssh/config \
  && chmod 600 ~/.ssh/config \
  && ssh-keyscan github.com >> ~/.ssh/known_hosts 2>/dev/null'

mkdir -p /opt/ethiobuildconnect && chown deploy:deploy /opt/ethiobuildconnect
sudo -u deploy git clone \
  git@github.com:habtamuworkugselassie/housing-platform-backend.git \
  /opt/ethiobuildconnect/housing-platform-backend
```

The SSH remote is what makes the workflow's later `git pull --ff-only` work without
a prompt.

## 4. Fill in the secrets

```bash
cd /opt/ethiobuildconnect/housing-platform-backend
sudo -u deploy cp deploy/.env.example deploy/.env
chown deploy:deploy deploy/.env && chmod 600 deploy/.env
openssl rand -base64 32   # -> POSTGRES_PASSWORD
openssl rand -base64 48   # -> JWT_SECRET  (min 32 chars, enforced at startup)
nano deploy/.env
```

Owned by `deploy` and `600`: the workflow runs compose as that user, and compose
reads this file. Root-owned, the deploy fails with a confusing interpolation error
rather than a permission one.

Every `REQUIRED` line in that file has no usable default. `deploy/.env` is gitignored —
keep it that way.

## 5. First start

The app images do not exist on the droplet yet — nothing has deployed. Bring up the
infrastructure first, then let a deploy supply the rest:

```bash
# postgres, redis and caddy pull from public registries and start immediately.
# --no-deps matters: caddy depends on frontend, which depends on backend, and
# without it compose follows that chain and fails on
#   "No such image: habtamuwgs/housing-platform-backend:latest"
# — the image the workflow has not delivered yet.
docker compose -f deploy/docker-compose.prod.yml up -d --no-deps postgres redis caddy
```

Caddy starts without a frontend behind it; it resolves `frontend:80` per request and
answers 502 until the deploy lands.

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

`V52__Bootstrap_super_admin.sql` creates `superadmin@ethiobuildconnect.et`. Only the
bcrypt hash of its password is in the repository; the plaintext is recorded nowhere, and
the migration's own header says to treat it as compromised. So do not look for it — set
a new one:

```bash
bash deploy/reset-password.sh                      # superadmin@ethiobuildconnect.et
bash deploy/reset-password.sh someone@example.com  # any other account
```

It prompts for the password (never echoed or logged), hashes it inside the postgres
container with bcrypt cost 10 — the format the app verifies — and enforces the app's
own policy (8+ characters, upper, lower, digit) so it can be changed from the UI later.
Run it as root or the deploy user.

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

## 9. Where the data lives — and backing it up, on day one

The database and uploaded media are **plain directories on the droplet**, bind-mounted
into the containers:

| What | Host path | Mounted in |
|---|---|---|
| PostgreSQL data | `/var/lib/ethiobuildconnect/postgres` | `postgres` |
| Uploaded media (listing photos, videos, logos, documents) | `/var/lib/ethiobuildconnect/uploads` | `backend` (rw), `frontend` (ro, nginx serves them) |

They are deliberately not Docker volumes. Docker only mounts a host directory — it never
creates, owns or deletes one — so no `docker compose down -v`, `docker volume prune`,
`docker system prune --volumes` or project rename can remove them. Deploys replace
containers and images only. Only a person with a shell can delete these paths.

(`DATA_DIR` in `deploy/.env` moves the parent directory; set it before the first start
or not at all.) If the droplet was first set up when the stack still used named volumes,
`deploy/migrate-volumes-to-host.sh` copies them across; the deploy workflow runs it, and
it is a no-op once the directories have content. Remove the old volumes after verifying:
`docker volume rm ethiobuildconnect_postgres-data ethiobuildconnect_uploads`.

The previous database was lost. Nothing in this stack prevents that happening again
except a backup somewhere other than this disk.

```bash
mkdir -p /var/backups/ethiobuildconnect
cat > /etc/cron.daily/ethiobuildconnect-backup <<'EOF'
#!/bin/sh
set -e
cd /opt/ethiobuildconnect/housing-platform-backend
# Logical dump: restorable into any PostgreSQL 16 with pgvector, unlike a copy of the data dir.
docker compose -f deploy/docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
  | gzip > /var/backups/ethiobuildconnect/db-$(date +%F).sql.gz
# Media is not in the database.
tar czf /var/backups/ethiobuildconnect/uploads-$(date +%F).tar.gz \
  -C /var/lib/ethiobuildconnect uploads
find /var/backups/ethiobuildconnect -name '*.gz' -mtime +14 -delete
EOF
chmod +x /etc/cron.daily/ethiobuildconnect-backup
/etc/cron.daily/ethiobuildconnect-backup && ls -lh /var/backups/ethiobuildconnect
```

A backup on the same droplet does not survive losing the droplet. Copy these off the
box — DigitalOcean Spaces, or `scp` on a schedule — and enable droplet backups in the
DigitalOcean control panel.

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
