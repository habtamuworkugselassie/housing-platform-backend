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

Once the broadcaster has published, its client calls `POST /{id}/recording/start` with the
published audio+video track ids and the backend starts a **TrackComposite** file egress —
it muxes just those two tracks into one MP4 with **no headless Chromium**, so it runs on
the 1-vCPU host. The client publishes **H.264** (`publishDefaults.videoCodec`) so this is a
remux, not a transcode. The public replay URL is stored on the broadcast and served by the
frontend; on end (Stop, tab close, admin cut, reject) the egress is stopped and finalized.
Records the broadcaster only — co-hosts are not composited in (that needs RoomComposite).

### ⚠️ 2026-09-05 incident — recording rebooted the host

`LIVEKIT_RECORDING_ENABLED` was turned on while recording still used **RoomComposite**
(headless Chromium, ~4 vCPU). Every go-live spawned Chromium, exhausted the 1-vCPU/2 GB/
**no-swap** droplet and **hard-rebooted the box** (twice that day); the crash killed egress
before any MP4 finalized, so recordings were empty and DB `recording_url`s dangled.
Remediation: added a **4 GB swapfile** (`/swapfile`, in `/etc/fstab`, `vm.swappiness=10`),
switched recording to TrackComposite (this change), and cleared the dangling URLs. Re-enable
recording only on the TrackComposite build. RoomComposite (`startFileRecording`, dormant) is
for a future ≥4-vCPU host where co-host compositing is wanted.

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

### CPU note

TrackComposite (the current recorder) is light — no browser, and with H.264 it remuxes
rather than transcodes — so it runs on the 1-vCPU host. **RoomComposite** (full-room
compositing, needed to record co-hosts into one file) still wants **~4 vCPU** and will log
`not enough cpu for some egress types`; only switch back to it after resizing the droplet to
4+ vCPU (CPU-Optimized) or running egress on its own node.

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
