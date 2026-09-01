# Social simulcast (egress → YouTube / Facebook / TikTok)

The app can re-stream a live broadcast to external RTMP destinations in real time
using LiveKit **Egress** (RoomComposite → RTMP). This is the infra + operator
guide.

## 1. Egress must be running

`docker-compose.livekit.yml` already defines the `egress` service. Make sure it's
up alongside the server:

```bash
docker compose -f docker-compose.livekit.yml up -d redis livekit-server ingress egress
docker compose -f docker-compose.livekit.yml ps      # egress should be "running"
```

Egress renders the room in a headless browser to composite it, so it is **heavier
than the SFU** — give it a couple of CPU cores and ~2 GB RAM. Watch its logs the
first time:

```bash
docker compose -f docker-compose.livekit.yml logs -f egress
```

The backend talks to Egress over the same LiveKit API — no extra env beyond the
existing `LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET`.

## 2. Add a destination in the admin portal

**Admin → Live broadcasts → "Social destinations" → Add destination.** For each
platform you paste an **RTMP server URL** + **stream key**:

| Platform | RTMP server URL | Where to get the key |
|---|---|---|
| **YouTube** | `rtmp://a.rtmp.youtube.com/live2` | YouTube Studio → **Go Live** → *Stream* → "Stream key" |
| **Facebook** | `rtmps://live-api-s.facebook.com:443/rtmp/` | Facebook Live Producer → "Use a stream key" |
| **TikTok** | (only if your account has **LIVE / RTMP** access) | TikTok LIVE → RTMP → server + key |

Stream keys are stored server-side and **never shown again** (the admin UI only
shows whether a key is set). Leaving the key blank when editing keeps the existing one.

## 3. Go live on social

- **Manual:** on a **LIVE** broadcast, click **Simulcast**, tick the destinations,
  **Start**. Click **Stop simulcast** (or end the broadcast) to stop.
- **Automatic:** turn on **Admin → Display Settings → "Auto-simulcast on go-live"**
  and every organizer/admin stream pushes to all enabled destinations the moment
  it goes live.

## 4. YouTube quick test (recommended first run)

1. YouTube Studio → **Create → Go live → Stream**. Set visibility to **Unlisted**.
2. Copy the **Stream URL** (`rtmp://a.rtmp.youtube.com/live2`) and **Stream key**.
3. Add them as a destination (Platform: YouTube).
4. Start a broadcast on the portal, go live, then **Simulcast → YouTube → Start**.
5. Within ~10–20 s YouTube's dashboard shows the incoming stream; click **Go live**
   there. Verify audio + video, then **Stop simulcast**.

## Notes & gotchas

- **Facebook** requires `rtmps://` (TLS) — make sure the URL starts with `rtmps`.
- **TikTok** does not hand out RTMP keys freely; live RTMP needs TikTok LIVE
  permission. The system supports it only if you have a key.
- One egress fans out to **all selected destinations** at once — no need for a
  separate egress per platform.
- If a simulcast won't start, check the **egress** container logs first (CPU/RAM,
  or a rejected RTMP URL/key).
