package com.housingplatform.publicsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.publicsupport.dto.ChatMessageDto;
import com.housingplatform.publicsupport.dto.PublicSupportChatRequest;
import com.housingplatform.publicsupport.dto.PublicSupportChatResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicSupportChatService {

  private final ObjectMapper objectMapper;
  private final SupportChatRagContextService ragContextService;

  @Value("${app.support-chat.enabled:true}")
  private boolean supportChatEnabled;

  @Value("${app.support-chat.api-key:}")
  private String llmApiKey;

  @Value("${app.support-chat.api-base-url:https://api.openai.com/v1}")
  private String apiBaseUrl;

  @Value("${app.support-chat.model:gpt-4o-mini}")
  private String chatModel;

  @PostConstruct
  void logSupportChatConfig() {
    log.info(
        "Support chat: enabled={}, apiBaseUrl={}, model={}, llmApiKeyConfigured={}",
        supportChatEnabled,
        apiBaseUrl,
        chatModel,
        StringUtils.hasText(llmApiKey));
  }

  private static final String SYSTEM_PROMPT =
      """
      You are the in-app guide for Ethio Build Connect (Ethiopia)—real estate and construction. \
      Write for everyday users: short sentences, plain words, no jargon. Sound warm and \
      conversational, not like a manual.

      LOCATION AND FILTERS (critical):
      - The listings page filters mainly by **city name** (and optional status), not by small \
      neighborhood inside a city. Places like Bole, Kirkos, Megenagna, Piassa, Kazanchis, etc. \
      are **areas inside Addis Ababa** (or another city)—they are usually **not** a separate \
      value in the City box.
      - If someone asks for a **neighborhood** (e.g. "apartment in Bole"): (1) Acknowledge Bole is \
      in Addis Ababa. (2) Tell them to open the **listings** area (see SITE NAVIGATION below), set \
      **City** to **Addis Ababa** (or the correct city they mean). (3) Then **scroll the results** \
      and read titles, addresses, and \
      descriptions—many listings mention the sub-area there. (4) Optionally use the browser Find \
      (Ctrl+F or Cmd+F) on the page to search for "Bole" after results load. **Do not** tell them \
      to type "Bole" alone in the City filter unless the site actually uses that as a city name— \
      default is: city filter + look inside listings for the neighborhood.
      - For other cities (Hawassa, Bahir Dar, etc.), same idea: use that **city** in the filter, \
      then scan listings for the local area name if they gave one.

      INTERACTIVE STYLE:
      - When helpful, **ask one short follow-up question** at the end (e.g. "Are you looking to \
      rent or buy?" or "Roughly what budget range?" or "How many bedrooms?")—only if they have not \
      already answered. Do not interrogate; one question is enough.
      - If they already gave enough detail, answer directly without a redundant question.

      SITE NAVIGATION (must match the real UI—do not invent top-bar items):
      - The **top bar** shows: logo (home), **Marketplace** (dropdown), and **Login / Register** \
      when logged out. There is **no** separate **Properties** or **Exhibition** label in the \
      desktop top bar.
      - To browse homes and buildings: open **Marketplace** in the top bar, then **Real estate** \
      (route `/marketplace/real-estate`), **or** go to **`/properties`** for the combined list with \
      city filters (bookmark, footer, or type the path). On **small screens**, the mobile menu may \
      include a Properties entry.
      - **Exhibition** info and registration are on the **homepage** by scrolling (and footer links); \
      there is no global "Exhibition" section in the top bar. Do not tell users to click \
      "Exhibition" or "Properties" in the header—they are not there on desktop.

      HOW TO EXPLAIN LISTINGS:
      - On the listings page, explain the **City** filter and optional **status**. Mention `?city=` \
      in the URL only as an **optional** tip—**never** lead with raw URLs for basic questions.
      - Say sponsored listings may appear higher. Never invent listings, prices, or availability.

      DATABASE SNIPPETS:
      - Sometimes a block titled FACTUAL_SNIPPETS_FROM_DATABASE is appended with live data: \
      optional **VECTOR_RAG_HITS** (semantic search over a pgvector index of organizations, \
      properties, and sponsorship packages—refreshed when those records change), token-matched \
      **PROPERTIES** / **ORGANIZATIONS**, optional **ORGANIZATIONS_RANKED** / **PROPERTIES_RANKED** \
      (lexical/directory SQL when vector mode is off or hybrid), and **SPONSORSHIP_PACKAGES** when \
      included from the API. Use these for **specific** answers; cite verification and ratings \
      when comparing options; only use contacts shown in the snippet. Do not invent prices, \
      contacts, or ratings. If the block is empty or a topic is not covered there, rely on general \
      site guidance and do not make up inventory.

      SCOPE: Homepage exhibition blocks, marketplace, login/account—short accurate steps. If unsure \
      about policies, say so and point to Contact on the homepage.

      Length: about 120–180 words. Language: match the user for English or Amharic; otherwise \
      English.""";

  public PublicSupportChatResponse reply(PublicSupportChatRequest request) {
    if (!supportChatEnabled) {
      return new PublicSupportChatResponse(disabledMessage(), false);
    }
    validateTurns(request.getMessages());
    String userText = lastUserText(request.getMessages());
    String ragBlock = ragContextService.buildContextBlock(userText);
    if (StringUtils.hasText(llmApiKey)) {
      try {
        String text = callLlmChatCompletions(request.getMessages(), ragBlock);
        if (StringUtils.hasText(text)) {
          return new PublicSupportChatResponse(text.trim(), true);
        }
      } catch (RestClientException e) {
        log.warn("Support chat LLM request failed: {}", e.getMessage());
      } catch (Exception e) {
        log.warn("Support chat LLM error", e);
      }
    }
    return new PublicSupportChatResponse(fallbackReply(userText), false);
  }

  private void validateTurns(List<ChatMessageDto> messages) {
    if (messages.isEmpty()) {
      throw new IllegalArgumentException("messages required");
    }
    ChatMessageDto last = messages.get(messages.size() - 1);
    if (!"user".equalsIgnoreCase(last.getRole())) {
      throw new IllegalArgumentException("Last message must be from the user");
    }
  }

  private String lastUserText(List<ChatMessageDto> messages) {
    for (int i = messages.size() - 1; i >= 0; i--) {
      if ("user".equalsIgnoreCase(messages.get(i).getRole())) {
        return messages.get(i).getContent();
      }
    }
    return "";
  }

  private String callLlmChatCompletions(List<ChatMessageDto> turns, String ragBlock) {
    String base = apiBaseUrl.trim().replaceAll("/+$", "");
    RestClient client =
        RestClient.builder()
            .baseUrl(base)
            .defaultHeader("Authorization", "Bearer " + llmApiKey.trim())
            .build();

    String systemContent = SYSTEM_PROMPT;
    if (StringUtils.hasText(ragBlock)) {
      systemContent = SYSTEM_PROMPT + "\n\n" + ragBlock;
    }

    List<Map<String, String>> payloadMessages = new ArrayList<>();
    payloadMessages.add(Map.of("role", "system", "content", systemContent));
    for (ChatMessageDto t : turns) {
      payloadMessages.add(
          Map.of("role", t.getRole().toLowerCase(Locale.ROOT), "content", t.getContent().trim()));
    }

    Map<String, Object> body = new HashMap<>();
    body.put("model", chatModel);
    body.put("messages", payloadMessages);
    body.put("max_tokens", 800);
    body.put("temperature", 0.65);

    String raw =
        client
            .post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

    if (raw == null || raw.isBlank()) {
      return "";
    }
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode choice = root.path("choices");
      if (!choice.isArray() || choice.isEmpty()) {
        return "";
      }
      return choice.get(0).path("message").path("content").asText("");
    } catch (Exception e) {
      log.warn("Failed to parse LLM chat completions JSON", e);
      return "";
    }
  }

  private String disabledMessage() {
    return "Support chat is temporarily unavailable. Please use the Contact section on the homepage.";
  }

  private String fallbackReply(String userText) {
    String core = fallbackReplyCore(userText);
    String hints = ragContextService.buildFallbackHints(userText);
    if (StringUtils.hasText(hints)) {
      return core + "\n\n" + hints;
    }
    return core;
  }

  private String fallbackReplyCore(String userText) {
    String t = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
    if (t.contains("register") || t.contains("sign up") || t.contains("account")) {
      return "You can create an account from the Register link in the top navigation. "
          + "After registering, you can access dashboards and features based on your role.";
    }
    if (t.contains("exhibition") || t.contains("exhibit") || t.contains("visitor")) {
      return "Exhibition details and registration are on the homepage—scroll down, or use the footer "
          + "for FAQ and contact. There is no separate Exhibition item in the top bar; start from "
          + "the logo (home) or open the site root.";
    }
    if (t.contains("bole")
        || t.contains("kirkos")
        || t.contains("kazanchis")
        || t.contains("piassa")
        || t.contains("megenagna")
        || t.contains("gerji")
        || t.contains("summit")
        || t.contains("gotera")) {
      return "Places like Bole or Kirkos are areas inside Addis Ababa. Open Marketplace → Real "
          + "estate, or go to /properties, set City to Addis Ababa, then scroll the results and read "
          + "each listing—titles and addresses often mention the neighborhood. Use Find (Ctrl+F or "
          + "Cmd+F) on the page to search for the area name. Are you looking to rent or buy, and do "
          + "you have a rough budget in mind?";
    }
    if (t.contains("property")
        || t.contains("listing")
        || t.contains("house")
        || t.contains("apartment")
        || t.contains("villa")
        || t.contains("betoch")) {
      return "Use Marketplace → Real estate in the top bar, or open /properties. Use the City box "
          + "for the town (for example Addis Ababa or Hawassa). For a smaller area inside the city, "
          + "pick the city first, then read each listing. Open a card for photos and details. "
          + "Sponsored listings may appear first. Are you renting or buying?";
    }
    if (t.contains("location")
        || t.contains("place")
        || t.contains("area")
        || t.contains("city")
        || t.contains("where ")
        || t.contains(" neighborhood")
        || t.contains("addis")
        || t.contains("hawassa")) {
      return "From Marketplace → Real estate or /properties, choose the right city in the City "
          + "filter. The list shows homes and buildings for that city. For a neighborhood inside "
          + "that city, scroll and read the listings or use Find on the page. Tell me if you prefer "
          + "rent or buy and I can suggest what to look for next.";
    }
    if (t.contains("marketplace") || t.contains("bank") || t.contains("supplier")) {
      return "Click Marketplace in the top bar to open the dropdown—choose Real estate, Banks, "
          + "Suppliers, and other categories to see approved organizations and their profiles.";
    }
    if (t.contains("login") || t.contains("password") || t.contains("forgot")) {
      return "Use Login in the navigation to sign in. If you forgot your password, use the "
          + "forgot-password flow from the login page when available.";
    }
    return "I can walk you through the site. For listings, use Marketplace → Real estate or "
        + "/properties, set the city, then browse. The top bar does not show a separate Properties "
        + "or Exhibition item on desktop. What city are you interested in, and are you renting or "
        + "buying? For account problems, use Contact on the homepage.";
  }
}
