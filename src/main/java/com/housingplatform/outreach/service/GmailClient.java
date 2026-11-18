package com.housingplatform.outreach.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Minimal Gmail REST client: sends one-to-one mail from the authorised mailbox (over HTTPS, which
 * the droplet can reach, unlike SMTP ports) and reads a thread to see whether the recipient
 * answered. Needs an OAuth client (Google Cloud console) and a refresh token for the mailbox with
 * the gmail.send and gmail.readonly scopes; see deploy/README.md.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailClient {

  public record Sent(String messageId, String threadId) {}

  public record ThreadMessage(String from, Instant date, String snippet) {}

  private final OutreachProperties props;
  private final RestClient.Builder restClientBuilder;

  private volatile String accessToken;
  private volatile Instant accessTokenExpiry = Instant.EPOCH;

  public boolean configured() {
    return props.gmailConfigured();
  }

  public Sent send(
      String to, String subject, String body, String inReplyToMessageId, String threadId)
      throws Exception {
    MimeMessage mime = new MimeMessage((Session) null);
    mime.setFrom(new InternetAddress(props.getSenderEmail(), props.getSenderName(), "UTF-8"));
    mime.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(to));
    mime.setSubject(subject, "UTF-8");
    mime.setText(body, "UTF-8");
    if (inReplyToMessageId != null) {
      mime.setHeader("In-Reply-To", inReplyToMessageId);
      mime.setHeader("References", inReplyToMessageId);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    mime.writeTo(out);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());

    var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("raw", raw);
    if (threadId != null) payload.put("threadId", threadId);

    JsonNode res =
        client()
            .post()
            .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(JsonNode.class);
    return new Sent(res.path("id").asText(null), res.path("threadId").asText(null));
  }

  /** The RFC 822 Message-ID header of a sent message, for threading the follow-up. */
  public String messageIdHeader(String gmailMessageId) throws Exception {
    JsonNode res =
        client()
            .get()
            .uri(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/{id}?format=metadata&metadataHeaders=Message-ID",
                gmailMessageId)
            .retrieve()
            .body(JsonNode.class);
    for (JsonNode h : res.path("payload").path("headers")) {
      if ("message-id".equalsIgnoreCase(h.path("name").asText())) return h.path("value").asText();
    }
    return null;
  }

  public List<ThreadMessage> thread(String threadId) throws Exception {
    JsonNode res =
        client()
            .get()
            .uri(
                "https://gmail.googleapis.com/gmail/v1/users/me/threads/{id}?format=metadata&metadataHeaders=From",
                threadId)
            .retrieve()
            .body(JsonNode.class);
    List<ThreadMessage> out = new ArrayList<>();
    for (JsonNode m : res.path("messages")) {
      String from = "";
      for (JsonNode h : m.path("payload").path("headers")) {
        if ("from".equalsIgnoreCase(h.path("name").asText())) from = h.path("value").asText();
      }
      long ms = m.path("internalDate").asLong(0);
      out.add(new ThreadMessage(from, Instant.ofEpochMilli(ms), m.path("snippet").asText("")));
    }
    return out;
  }

  private RestClient client() throws Exception {
    return restClientBuilder.defaultHeader("Authorization", "Bearer " + accessToken()).build();
  }

  private synchronized String accessToken() throws Exception {
    if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(60))) {
      return accessToken;
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", props.getGmail().getClientId());
    form.add("client_secret", props.getGmail().getClientSecret());
    form.add("refresh_token", props.getGmail().getRefreshToken());
    form.add("grant_type", "refresh_token");
    JsonNode res =
        restClientBuilder
            .build()
            .post()
            .uri("https://oauth2.googleapis.com/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
    accessToken = res.path("access_token").asText();
    accessTokenExpiry = Instant.now().plusSeconds(res.path("expires_in").asLong(3600));
    return accessToken;
  }

  public boolean isOwnAddress(String from) {
    return from != null
        && from.toLowerCase(Locale.ROOT).contains(props.getSenderEmail().toLowerCase(Locale.ROOT));
  }
}
