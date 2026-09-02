# LiveKit production runbook & incident log

Operational notes for the self-hosted LiveKit stack on the DigitalOcean droplet
(`209.38.204.219`, compose at `/root/livekit/docker-compose.livekit.yml`, reverse
proxy at `/root/housing-platform/Caddyfile`). Keep this in sync with reality.

## Topology (production)

```
browser ──wss──▶ Caddy (live.ethiobuildconnect.et:443) ──▶ livekit:7880   (signaling)
browser ──WebRTC UDP 50000-50200 / TCP 7881──────────────▶ livekit         (media)
livekit ──▶ redis                                                          (coordination)
egress  ──▶ livekit (ws://livekit:7880), writes MP4 ▶ /out (= uploads vol) (recording)
```

All LiveKit containers run on a **single** docker network (`livekit_livekit`).
Caddy is attached to that network **and** the app's `default` network.

## Incident 2026-09-02 — go-live failing (three stacked bugs)

1. **No inbound media — dual docker network.** The `livekit` container was on two
   networks (`housing-platform_default` 172.18.x + `livekit_livekit` 172.19.x).
   Docker's published-port DNAT delivered inbound RTC packets to the 172.18 address
   while pion bound its ICE sockets on 172.19, so every connectivity check was
   dropped inside the container. ICE never completed → "could not establish pc
   connection". **Fix:** single-home livekit/ingress/egress to `networks: [livekit]`
   and attach Caddy to `livekit_livekit`.

2. **Video publish failed — server too old for the client SDK.** Frontend
   `livekit-client` 2.22 (protocol 17) against server **v1.8.4** could publish audio
   but video died with `UnexpectedConnectionState: pcManager is not ready` (the room
   bounced to `reconnecting` mid-publish); viewers saw a black player. **Fix:** bump
   the server image to **v1.9** (running v1.9.12). Connect dropped from ~9 s to ~1.7 s
   (the old `/rtc/v1` 404-retry also disappeared) and both tracks publish in ~250 ms.
   Keep the server version in step with the client SDK.

3. **Recording/simulcast couldn't run — egress not started.** Only `livekit` + `redis`
   were up. **Fix:** start the `egress` container (below).

Backups of the pre-change compose files are on the server as `*.bak*`.

## Recording (local MP4)

When a broadcast goes live the backend starts a RoomComposite **file** egress that
writes one MP4 into the shared uploads volume; the public replay URL is stored on the
broadcast and served by the frontend. On end (Stop broadcasting, tab close, admin cut,
reject) the egress is stopped and the file finalized.

- Egress mounts `/var/lib/housing-platform/uploads:/out`; backend writes to
  `/out/live-recordings/<room>-<ts>.mp4` (`livekit.recording-dir`) and stores the URL
  `/api/v1/uploads/live-recordings/<file>.mp4` (`livekit.recording-public-base`), which
  the frontend nginx already serves from that volume.
- Egress needs its config via **`EGRESS_CONFIG_FILE=/etc/egress.yaml`** (the `--config`
  flag is ignored by the image) and the api key/secret **inline** in `egress.yaml`
  (the server copy is filled from `.env`; the repo copy is secret-free — do not commit
  keys).
- Enable it on the backend with `LIVEKIT_RECORDING_ENABLED=true` (default off).

Start / restart egress:

```bash
cd /root/livekit
docker compose -f docker-compose.livekit.yml up -d egress
docker logs livekit-egress-1 --tail 20
```

### ⚠️ CPU requirement

RoomComposite egress runs a headless Chromium compositor and wants **~4 vCPU**. The
current droplet is **1 vCPU / 2 GB**, and egress logs `not enough cpu for some egress
types (minimumCpu 4, available 1)`. Recording (and the co-host multi-tile composite)
will be unreliable or fail until you either:
- resize the droplet to 4+ vCPU (CPU-Optimized), or
- run egress on a separate CPU-optimized node pointing at the same redis + livekit, or
- back recordings with object storage and accept reduced quality.

Until then, `LIVEKIT_RECORDING_ENABLED` may be left `false` in production.

## Redeploy (how app code reaches production)

Push to `main` → GitHub Actions builds the image to GHCR → SSHes to the droplet,
retags to `habtamuwgs/housing-platform-{frontend,backend}:latest`, and restarts.
Infra/compose changes on the droplet are **not** in that pipeline — apply them by hand
and mirror them here under `infra/livekit/`.

## Quick health checks

```bash
docker ps                                   # livekit, redis, egress, caddy all Up
curl -s https://live.ethiobuildconnect.et/  # -> OK (signaling reachable)
docker logs livekit-livekit-1 --tail 5      # version 1.9.x, node listening
```
