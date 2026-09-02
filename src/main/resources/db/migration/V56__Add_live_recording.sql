-- Local recording of a live broadcast: a separate RoomComposite egress writes an MP4
-- file to the shared uploads volume when the stream goes live, and its public URL is
-- stored here once the broadcast ends so viewers can watch the replay.

-- Egress id of the file recording (kept apart from egress_id, which tracks social simulcast).
ALTER TABLE live_broadcast ADD COLUMN IF NOT EXISTS recording_egress_id VARCHAR(64);

-- Public URL of the finished recording (served from the uploads volume), null until ended.
ALTER TABLE live_broadcast ADD COLUMN IF NOT EXISTS recording_url VARCHAR(512);
