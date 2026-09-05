# Self-hosted LiveKit (Phase 5 live broadcasting)

Bring up the media backbone that powers live broadcasting (visitor/exhibitor
phones & browsers over WebRTC, organizer professional cameras over RTMP/WHIP,
viewers over HLS).

## Run
1. `cp .env.livekit.example .env` and set a strong `LIVEKIT_API_SECRET`
   (`openssl rand -hex 32`); use the same value in `LIVEKIT_KEYS`.
2. Put TLS certs for `live.<domain>` at the path referenced by `livekit.yaml`,
   and set `rtc.use_external_ip` / the TURN `domain` for your host.
3. `docker compose -f docker-compose.livekit.yml up -d`
4. Reverse-proxy `wss://live.<domain>` → `livekit:7880`.

## Wire to the app
The Spring backend mints LiveKit access tokens (it never exposes the secret to
clients). Set on the backend:

    LIVEKIT_URL=wss://live.ethiobuildconnect.et
    LIVEKIT_API_KEY=<same as here>
    LIVEKIT_API_SECRET=<same as here>

Broadcasters get a **publish** token only after an organizer approves their
go-live request; viewers get a **subscribe-only** token (or the HLS URL). An
organizer can cut any stream via the admin kill-switch.

## Recording (MP4 replays)

Each live room can be recorded to an MP4 that viewers replay from the public
live page (**Past broadcasts**, `GET /api/v1/exhibition/live/replays`).
Recording is off by default and needs all three of:

1. **Enable it** on the backend: `LIVEKIT_RECORDING_ENABLED=true`, then
   `docker compose up -d backend`.
2. **Egress must write into the served uploads volume.** The egress service
   mounts `/var/lib/housing-platform/uploads:/out` (see the compose file) so
   files land in `/out/live-recordings`, which the frontend serves at
   `/api/v1/uploads/live-recordings/`. Create the folder and make it writable
   by the egress user (**uid 1001**, gid 0) — a root-owned dir fails silently:

       sudo mkdir -p /var/lib/housing-platform/uploads/live-recordings
       sudo chown -R 1001:0 /var/lib/housing-platform/uploads/live-recordings
       sudo chmod -R 775 /var/lib/housing-platform/uploads/live-recordings

3. **Enough CPU.** RoomComposite egress (the compositor behind recording, HLS
   and social simulcast) drives a headless Chromium and **requires ≥ 4 vCPU**.
   On a smaller host egress logs `not enough cpu for some egress types
   {"minimumCpu": 4}` and declines the job, which the backend sees as
   `503 "no response from servers"`. Plan ~8 GB RAM. A 1 vCPU / 2 GB droplet
   cannot record; forcing it via `cpu_cost.room_composite_cpu_cost` will OOM
   the host and take SSH down with it.

The backend pre-creates the room before starting egress (LiveKit creates rooms
lazily, and egress must attach to an existing room), records from the moment
the broadcaster publishes, and finalizes the file when the stream ends.
Recording is best-effort — a failure never blocks a go-live. To see why a
recording didn't happen:

    docker logs housing-platform-backend 2>&1 | grep -i "recording"
    docker logs livekit-egress-1 --tail 60

## Ports
- 7880 signaling/API (TLS proxy), 7881 ICE/TCP, 5349 TURN/TLS, 50000-50200/udp media
- 1935 RTMP, 8085 WHIP (organizer camera ingest)

Pin image tags in the compose file to a tested LiveKit release and bump
server/ingress/egress together.

## Organizer professional camera (OBS → Ingress)

1. In **Admin → Live broadcasts**, find (or create via `/go-live` as Organizer)
   the broadcast and click **Pro camera**. The app calls
   `POST /api/v1/admin/exhibition/live/{id}/ingress` and shows an **RTMP server
   URL** + **stream key** (WHIP is also supported via `?type=WHIP`).
2. In **OBS → Settings → Stream**: Service = *Custom*, Server = the RTMP URL,
   Stream Key = the key. **Start Streaming**.
3. The feed lands in the broadcast's LiveKit room and appears on the public
   live wall (WebRTC now, HLS once egress is running). **Cut** in the admin
   view force-stops it and deletes the ingress.

The backend mints the ingress via LiveKit's `IngressService` using an
`ingressAdmin` token — the API secret never leaves the server.
