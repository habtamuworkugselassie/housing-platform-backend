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
