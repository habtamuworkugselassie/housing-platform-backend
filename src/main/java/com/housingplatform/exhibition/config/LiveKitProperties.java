package com.housingplatform.exhibition.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Self-hosted LiveKit connection details. The API secret never leaves the server — clients only
 * ever receive short-lived access tokens minted from it.
 */
@Component
@ConfigurationProperties(prefix = "livekit")
@Getter
@Setter
public class LiveKitProperties {

  /** Public signaling URL clients connect to, e.g. wss://live.ethiobuildconnect.et */
  private String url = "";

  private String apiKey = "";

  private String apiSecret = "";

  /**
   * Directory the egress container writes MP4 recordings to. This is a path *inside the egress
   * container*, which mounts the shared uploads volume — e.g. /out/live-recordings maps to the
   * host uploads dir served at {@link #recordingPublicBase}.
   */
  private String recordingDir = "/out/live-recordings";

  /** Public URL base the finished recording is served from (uploads volume via the frontend). */
  private String recordingPublicBase = "/api/v1/uploads/live-recordings";

  /** Master switch: only start file recordings when true (needs the egress container running). */
  private boolean recordingEnabled = false;

  public boolean isConfigured() {
    return !url.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
  }

  /** Base HTTP(S) URL for the LiveKit server (Twirp) API, derived from the ws(s) url. */
  public String httpUrl() {
    if (url.startsWith("wss://")) {
      return "https://" + url.substring("wss://".length());
    }
    if (url.startsWith("ws://")) {
      return "http://" + url.substring("ws://".length());
    }
    return url;
  }
}
