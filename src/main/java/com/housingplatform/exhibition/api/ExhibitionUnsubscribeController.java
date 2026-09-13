package com.housingplatform.exhibition.api;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.service.ExhibitionInterestService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The unsubscribe link at the bottom of every reminder.
 *
 * <p><b>Why the GET does not unsubscribe anyone.</b> Corporate mail gateways, spam filters and
 * link-preview scanners fetch every URL in a message before a human ever sees it. If the GET
 * carried out the opt-out, those scanners would quietly unsubscribe registrants who never clicked
 * anything, and nobody would find out until the mail stopped arriving. So the GET renders a page
 * with a button and the POST does the work — a scanner will not press it.
 *
 * <p>Served as HTML from the backend rather than handed to the SPA, because this page has to work
 * for someone who has no account, may be on a slow connection and has already decided to leave. A
 * self-contained page needs no JavaScript, no bundle and no API round trip of its own.
 */
@RestController
@RequestMapping("/api/v1/exhibition/interest/unsubscribe")
@Tag(name = "Exhibition", description = "Expo reminder opt-out (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class ExhibitionUnsubscribeController {

  private final ExhibitionInterestService service;
  private final ExpoProperties expo;

  @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
  @Operation(
      summary = "Show the reminder opt-out confirmation page",
      description =
          "Renders a confirmation page. Nothing is changed until the form on it is submitted, so"
              + " mail scanners that prefetch links cannot unsubscribe anyone.")
  public ResponseEntity<String> confirmPage(@RequestParam(name = "token") String token) {
    return html(
        page(
            "Stop reminders about " + expo.getName() + "?",
            "<p>We will stop sending you reminders about "
                + HtmlUtils.htmlEscape(expo.getName())
                + ". You will still receive a reply if you have an enquiry open with our"
                + " team.</p>"
                + "<form method=\"post\">"
                + "<input type=\"hidden\" name=\"token\" value=\""
                + HtmlUtils.htmlEscape(token == null ? "" : token)
                + "\">"
                + "<button type=\"submit\">Stop sending me reminders</button>"
                + "</form>"));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.TEXT_HTML_VALUE)
  @Operation(summary = "Opt out of expo reminders")
  public ResponseEntity<String> unsubscribe(@RequestParam(name = "token") String token) {
    boolean found = service.unsubscribe(token);
    if (!found) {
      // 404 for an unknown token, but worded as a dead link rather than an accusation: the usual
      // cause is a truncated URL from a mail client that wrapped the line, not a forged token.
      return ResponseEntity.status(404)
          .headers(htmlHeaders())
          .body(
              page(
                  "That link did not work",
                  "<p>This unsubscribe link is not one we recognise. It may have been cut short"
                      + " by your email program. Reply to any of our emails and we will take you"
                      + " off the list by hand.</p>"));
    }
    return html(
        page(
            "You are unsubscribed",
            "<p>You will not receive any more reminders about "
                + HtmlUtils.htmlEscape(expo.getName())
                + ". Nothing else changes, and you can still visit the site as usual.</p>"));
  }

  private static ResponseEntity<String> html(String body) {
    return ResponseEntity.ok().headers(htmlHeaders()).body(body);
  }

  private static HttpHeaders htmlHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("text/html;charset=UTF-8"));
    // Nothing here should sit in a shared cache: the page is keyed on a personal token.
    headers.setCacheControl("no-store");
    headers.add("X-Robots-Tag", "noindex, nofollow");
    return headers;
  }

  /** The brand's purple and gold, inline, so the page needs nothing from the frontend build. */
  private static String page(String heading, String bodyHtml) {
    return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<meta name=\"robots\" content=\"noindex, nofollow\">"
        + "<title>"
        + HtmlUtils.htmlEscape(heading)
        + "</title><style>"
        + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
        + "background:#4c1d95;color:#f5f3ff;font:16px/1.6 system-ui,-apple-system,"
        + "'Segoe UI',Roboto,sans-serif;padding:24px}"
        + "main{max-width:34rem;width:100%;background:#5b21b6;border-radius:16px;padding:32px}"
        + "h1{margin:0 0 16px;font-size:1.4rem;line-height:1.3;color:#f4c977}"
        + "p{margin:0 0 20px;color:#ede9fe}"
        + "button{appearance:none;border:0;border-radius:10px;padding:12px 20px;font:inherit;"
        + "font-weight:600;background:#f4c977;color:#2e1065;cursor:pointer}"
        + "button:hover{background:#e9b356}"
        + "a{color:#f4c977}"
        + "</style></head><body><main><h1>"
        + HtmlUtils.htmlEscape(heading)
        + "</h1>"
        + bodyHtml
        + "</main></body></html>";
  }
}
