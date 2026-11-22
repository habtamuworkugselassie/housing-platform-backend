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
# The API runs in one of two slots, backend-blue or backend-green (see step 10).
docker compose -f deploy/docker-compose.prod.yml logs -f backend-blue backend-green
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
starts the new release and waits on a health check — rolling back to the previous image
if the new one does not come up.

The API deploy is zero-downtime. The compose file defines two identical slots,
`backend-blue` and `backend-green`, that both answer to the hostname `backend`.
`deploy/rollout-backend.sh` starts the idle slot on the new image, waits for it to be
healthy (Flyway has run by then), and only then stops the slot that was serving; the
frontend's nginx resolves `backend` per request, so traffic moves over without a gap.
If the host has too little free memory for two JVMs, the script says so and falls back
to stop-then-start. To restart the API by hand, run the script rather than
`up -d backend-blue`, so the right slot is chosen.

Nothing needs doing on the droplet, including for changes to the stack itself: the
deploy runs `git pull` in this checkout first, so an edit to
`deploy/docker-compose.prod.yml` ships with the commit that makes it.

Flyway applies new migrations on restart. **Take a backup first (step 9)** — the health
gate rolls back the image, but it cannot roll back a migration.

---

## 11. Live broadcasting (LiveKit) — opt-in

The live pages (`/go-live`, the live wall, replays) need a LiveKit server. It is part of
this compose file behind the **`live` profile**: three services (`livekit`,
`livekit-redis`, `livekit-egress`) that start only when the profile is on, so the rest of
the stack never depends on them.

**Read this first — the host is small.** This is a 1 vCPU / 2 GB droplet, the same size
as the one LiveKit ran on before. That one **hard-rebooted twice** when recording was
enabled in its Chromium-based mode (`infra/livekit/RUNBOOK.md`). What runs here is the
safe shape — recording is a remux of the broadcaster's own tracks, no browser — but the
JVM, Postgres, LiveKit and egress together still exceed 2 GB under load. Two mitigations,
in order of preference:

1. **Resize the droplet** to 2 vCPU / 4 GB (DigitalOcean → droplet → Resize → CPU and RAM
   only; a few minutes of downtime, reversible). This is the honest fix.
2. **At minimum, add swap** so a spike degrades instead of rebooting the box:

   ```bash
   fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab
   sysctl -w vm.swappiness=10 && echo 'vm.swappiness=10' >> /etc/sysctl.d/99-swap.conf
   free -h
   ```

Then:

```bash
# 1. DNS: an A record  live  ->  the droplet IP, next to @ and www (DigitalOcean → Domains).
#    Caddy gets the certificate for it on its own once the name resolves.

# 2. Firewall: media goes straight to the droplet, not through Caddy.
ufw allow 7881/tcp     # ICE over TCP (fallback for UDP-blocked networks)
ufw allow 7882/udp     # all WebRTC media, one muxed port
ufw allow 3478/udp     # TURN

# 3. Secrets, in deploy/.env (as deploy, or chown afterwards):
#      COMPOSE_PROFILES=live
#      LIVEKIT_API_KEY=ebc
#      LIVEKIT_API_SECRET=<openssl rand -hex 32>
#      LIVEKIT_RECORDING_ENABLED=true
#    LIVEKIT_URL stays wss://live.ethiobuildconnect.et.

# 4. Start it, restart the backend so it picks up the LiveKit settings, and have Caddy
#    load the live.* block (a graceful reload, no downtime).
su - deploy -c "cd /opt/ethiobuildconnect/housing-platform-backend \
  && docker compose -f deploy/docker-compose.prod.yml up -d livekit livekit-egress \
  && bash deploy/rollout-backend.sh \
  && docker compose -f deploy/docker-compose.prod.yml exec -T caddy caddy reload --config /etc/caddy/Caddyfile \
  && docker compose -f deploy/docker-compose.prod.yml ps"
```

Verify: `curl -s https://live.ethiobuildconnect.et/` prints `OK`, and in the app an
organizer can go live from `/go-live` and see themselves on the live wall. Recordings
appear under `/var/lib/ethiobuildconnect/uploads/live-recordings/` after a broadcast
ends (the directory is created, owned by egress's uid 1001, by
`migrate-volumes-to-host.sh`).

Once `COMPOSE_PROFILES=live` is in `.env`, every deploy also applies changes to
`livekit.yaml` and the egress config. Turn it all off again by removing that line and
running `docker compose -f deploy/docker-compose.prod.yml --profile live down livekit
livekit-egress livekit-redis` — the rest of the stack is untouched.

Not included, deliberately: RTMP/WHIP ingest for professional cameras (`infra/livekit/`
has it) — ingress transcodes on the CPU and is not viable on 1 vCPU — and TURN over TLS,
which needs the certificate files shared from Caddy and a restart on every renewal.
Both are a profile and a config block away once the host is bigger.

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

---

## 12. Partner and exhibitor outreach (admin > Outreach)

A one-to-one sequencer for the curated prospect list. Import the CSV, add a personal
opening line per contact, and either queue contacts (the scheduler sends the sector template
in the contact's language, capped at 30 a day and 8 an hour, weekdays 09:00–17:00 Addis time,
with one follow-up after five business days) or open a contact and **send now** with the
template prefilled and edited as you like. Every email goes through the same Brevo relay as
the rest of the app, carries the package summary in the body, the brochure and sponsorship
PDFs in the contact's language as attachments, a registration link for the portal, the
signature and an opt-out line. Replies land in the reply-to mailbox; with the IMAP settings
filled in, replies and opt-outs are marked automatically, and contacts who register on the
portal are marked REGISTERED. OPTED_OUT, REPLIED, REGISTERED and BOUNCED contacts are never
emailed again, and an import cannot overwrite them.

Setup, once:
1. In Brevo, make sure the sender address (`OUTREACH_SENDER_EMAIL`, on the authenticated
   domain) exists as a sender, or use the same address as `MAIL_FROM`.
2. In `deploy/.env` set the `OUTREACH_*` values from `.env.example`; for automatic reply
   detection create a Gmail app password for the reply-to mailbox and put it in
   `OUTREACH_IMAP_PASSWORD`. Roll out.
3. In admin > Outreach: import the CSV, preview a few emails, send yourself a test, then
   send individually, or switch **Scheduled sending** on in the page header. With
   "Email new contacts automatically" on (the default) the scheduler takes NEW contacts
   by itself, oldest first; turn it off to send only what you queue by hand. The switch, caps, send window and follow-up delay are stored in the database
   (`outreach_settings`), so changing them needs no restart; the `OUTREACH_*` values in
   `.env` only seed the first row.

---

## 13. Visitor pre-registration and QR badges

Visitors register at `/register-visitor` (Amharic: `/am/register-visitor`). Each registration gets
a code like `EBC-K7M2PX`, a badge page at `/badge/<code>` and an email with the QR code inline
(sent through the normal Brevo sender; nothing extra to configure). Registering again with the
same email keeps the code and resends the badge. The public endpoint is rate limited like the
other lead forms.

At the door, admin > Visitors has a scanner (phone camera) and a code box; a badge can only be
checked in once. The same page shows totals by day and by visitor type and exports a CSV for the
badge printer.

---

## 14. Sponsorship package reservations (admin > Reservations)

A sponsor clicks "Reserve this package" on a tier (site or `/reserve`), fills company, contact,
email, phone, TIN and booth preference, and gets a reference (`RSV-XXXXXX`). The backend emails
them the exhibitor agreement and package sheet (PDFs from `resources/outreach/attachments`) with
the bank accounts, and notifies `EXHIBITION_NOTIFY_EMAIL`. Admin > Reservations shows the
pipeline (count and ETB per status), lets you set status, amount paid, owner and notes, resend
the agreement, or send a reminder. Reservations that sit untouched for five business days get an
automatic reminder (max two) at 09:30 Addis time on weekdays. The bank accounts live in
`SponsorshipPaymentDetails`; change them there and in the brochure together.

### Booth map

The 120 booths of the Addis International Hall plan (blocks A–D plus perimeter 01–20, from the
exhibitor agreement) are seeded by V79. A reservation may pick up to the package's booth count
(Exclusive 6, Platinum 3, Gold 2, Silver 1, Special none); picked booths are HELD for 10
business days and freed by the morning job if the reservation has not moved, SOLD once it is
paid, released if cancelled. Admin > Reservations has the map to change a booth's type, block
it, or assign booths to a reservation. The public map (`GET /api/v1/sponsorship-reservations/booths`)
shows status only, never who holds a booth.

### Online payments and reminders

A sponsor can pay the deposit (`RESERVATION_DEPOSIT_PERCENT`, default 30) or the balance through
the existing Chapa checkout from the reservation page (`/reserve/<reference>?email=...`). A paid
deposit makes the booth hold permanent; full payment marks the booths sold. The Chapa webhook is
shared with property deposits, and pending checkouts are re-verified every 15 minutes in case a
webhook is missed. Registered visitors get a "this week" reminder with their badge between
`VISITOR_REMINDER_FROM` and `VISITOR_REMINDER_UNTIL` (defaults 7–12 January 2027), 200 per hour.


## 15. Exhibitor self-service (portal at /exhibitor)

When a reservation reaches DEPOSIT_PAID or PAID (admin edit, manual entry, or a Chapa payment),
the backend provisions the exhibitor account after the transaction commits:

1. The organization is found through the contact's existing user account or contact email, or
   created from the reservation (company, TIN, phone; type `SUPPLIER` until the exhibitor changes
   it in the portal).
2. The contact becomes the organization's primary user (`PENDING_VERIFICATION` until they set a
   password) and an approved sponsorship for the reserved package is recorded, so the organization
   is listed publicly and appears on the sponsor strip once its logo is approved.
3. An invitation email with a 72-hour set-password link (`/reset-password?token=…&welcome=1`)
   goes to the contact. An admin can trigger or repeat this from Reservations ("Create exhibitor
   account" / "Re-send portal invite").

In the portal the exhibitor edits the company profile and uploads a logo, photos and videos.
Uploads from exhibitor accounts are stored with `review_status = PENDING`; they are hidden from
public responses (and from the sponsor strip) until an admin approves them under Reservations >
Exhibitor uploads. Each exhibitor upload emails `EXHIBITION_NOTIFY_EMAIL`, and the exhibitor is
emailed when an item is approved or rejected. Admin uploads stay approved on save.

Password resets now also activate a `PENDING_VERIFICATION` user, so the invitation link is enough
to sign in.

## 16. Lead nudges and test-data cleanup

Exhibitor and partner leads that chose a package but never reserved get the "Reserve your
place" email automatically (weekdays 10:00 Addis): first `LEAD_NUDGE_FIRST_DAYS` business days
after registration (default 3), once more `LEAD_NUDGE_AGAIN_DAYS` business days after any
invitation (default 7), at most `LEAD_NUDGE_MAX` times (default 2). A reservation on the lead's
organization or email, an outreach opt-out or bounce, or a manual "Invite to reserve" in the
meantime all count. `LEAD_NUDGE_ENABLED=false` switches it off.

`deploy/cleanup-test-data.sql` removes test organizations, leads, reservations, payments, media
and placeholder users. It runs as a dry run (reports, then `ROLLBACK`); edit the name and
reference lists at the top, check the report, then change the last line to `COMMIT`.
