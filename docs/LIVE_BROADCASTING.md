# Live broadcasting

End-to-end reference for the go-live / watch-live experience: gated go-live,
viewing, chat/reactions, social simulcast, **local recording**, **co-hosting**,
and the ops runbook.

Infra + incident history: [`infra/livekit/RUNBOOK.md`](../infra/livekit/RUNBOOK.md).

## Roles & gating

- **Visitor** — anonymous; the broadcast id acts as their capability (to publish, end
  their own stream, and moderate their own co-hosts).
- **Exhibitor / Organizer** — must be signed in; actions are checked against the owning
  account. Organizer (admin) streams are auto-approved; visitor/exhibitor streams start
  `REQUESTED` and wait for an organizer to approve.

Status flow: `REQUESTED → APPROVED → LIVE → ENDED` (or `REJECTED`).

## HTTP API (`/api/v1/exhibition/live`)

| Method & path | Who | Purpose |
|---|---|---|
| `POST /request` | anyone | Request to go live (rate-limited per IP). |
| `GET /` | anyone | Wall of `LIVE` broadcasts. |
| `GET /{id}` | anyone | Poll a broadcast's status. |
| `GET /{id}/publish-token` | broadcaster | Publish token — only when `APPROVED`/`LIVE`. Starts recording + auto-simulcast. |
| `GET /{id}/viewer-token` | anyone | Subscribe-only token. |
| `POST /{id}/end` | broadcaster | End own stream: finalize recording, stop simulcast, close room → `ENDED`. Idempotent. |
| `POST /{id}/cohost/request` | viewer | Ask to co-host a `LIVE` room. |
| `GET /{id}/cohost/requests` | broadcaster | Pending co-host queue. |
| `POST /{id}/cohost/{reqId}/approve` \| `/deny` | broadcaster | Moderate a request. |
| `GET /{id}/cohost/{reqId}/token` | viewer | Publish token — only once `APPROVED` (viewer polls). |

Admin/moderation endpoints (approve/reject/end kill-switch, simulcast start/stop,
ingress) live under the admin API and are unchanged.

## Tokens

`LiveKitTokenService` mints HS256 JWTs with a `video` grant. Broadcasters and approved
co-hosts get `canPublish:true`; plain viewers get `canPublish:false` (subscribe-only).
The API secret never leaves the server.

## Recording (local MP4)

On go-live the backend starts a RoomComposite **file** egress (`livekit.recording-enabled`,
default **off**). It writes `/out/live-recordings/<room>-<ts>.mp4` — the egress container
mounts the app uploads volume at `/out` — and stores the public URL
`/api/v1/uploads/live-recordings/<file>.mp4` on the broadcast (`recordingUrl`). Ending the
stream (Stop, tab close, admin cut, reject) stops the egress and finalizes the file.

⚠️ RoomComposite egress needs **~4 vCPU** (headless Chromium). The current 1-vCPU droplet
cannot record reliably — keep `LIVEKIT_RECORDING_ENABLED=false` until it is resized or
egress moves to a CPU-optimized node. See the runbook.

Config (env → `LiveKitProperties`): `LIVEKIT_RECORDING_ENABLED`, `LIVEKIT_RECORDING_DIR`,
`LIVEKIT_RECORDING_PUBLIC_BASE`.

## Co-hosting (approved viewers publish)

1. Viewer clicks **Join with camera** → `POST /cohost/request` (status `PENDING`).
2. Broadcaster's Go Live page polls `/cohost/requests` and taps **Approve**.
3. Viewer polls `/cohost/{reqId}/token`; on `APPROVED` it returns a publish token, the
   viewer reconnects to the same room with it and publishes camera/mic.
4. Everyone (viewers + broadcaster) renders each publisher as a tile
   (`useLiveRoom.remoteVideos` + `LiveVideoTile.vue`). The recording composites all tiles.

## Frontend touch points

- `views/GoLiveView.vue` — broadcaster: preview, publish, mic mute, multi-camera loop,
  **co-host moderation**, **end-on-close** (`sendBeacon`).
- `components/LivePlayer.vue` — viewer: play, chat, reactions, **audio mute**, **Join with camera**.
- `composables/useLiveRoom.ts` — room wrapper: chat/reactions/viewer-count, remote video
  tiles, remote-audio attach + mute, local publish.
- `shared/components/NavBar.vue` — site-wide **Live** link (desktop + mobile).

## Deploy

App code deploys via GitHub Actions on push to `main` (build → GHCR → SSH to droplet →
retag `habtamuwgs/...:latest` → restart). Requires migrations **V56** (recording columns)
and **V57** (`live_cohost_request`). Infra/compose changes are applied on the droplet by
hand and mirrored under `infra/livekit/` — they are **not** in the app pipeline.
