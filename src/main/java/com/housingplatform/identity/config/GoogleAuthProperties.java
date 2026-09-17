package com.housingplatform.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** "Sign in with Google": the OAuth client id the frontend button is registered under. */
@Component
@ConfigurationProperties(prefix = "google.oauth")
@Getter
@Setter
public class GoogleAuthProperties {

  /** Web client id from Google Cloud Console; empty disables the endpoint. */
  private String clientId = "";

  private String jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";

  public boolean isConfigured() {
    return clientId != null && !clientId.isBlank();
  }
}
