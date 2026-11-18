package com.housingplatform.outreach.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** app.outreach.* in application.yml; values come from the environment on the droplet. */
@Component
@ConfigurationProperties(prefix = "app.outreach")
@Getter
@Setter
public class OutreachProperties {
  /** Master switch for the scheduler. Admin screens and "send now" work either way. */
  private boolean enabled = false;

  private int dailyCap = 30;
  private int hourlyCap = 8;

  /**
   * Local hours (inclusive start, exclusive end) in which scheduled emails go out, weekdays only.
   */
  private int sendWindowStart = 9;

  private int sendWindowEnd = 17;
  private String timezone = "Africa/Addis_Ababa";

  /** Business days between the first email and the single follow-up. */
  private int followUpAfterDays = 5;

  private String senderName = "Ethio Build Connect";
  private String senderTitle = "";
  private String senderPhone = "";

  /** From address; must be on the domain authenticated in Brevo. Defaults to app.mail.from. */
  private String senderEmail = "";

  /** Where replies land (the expo mailbox). Defaults to app.mail.reply-to. */
  private String replyTo = "";

  /**
   * Mailbox that gets a blind copy of every outreach email sent to a contact. Defaults to replyTo.
   */
  private String copyTo = "";

  private String companyLine = "Dream Team Trading PLC, Addis Ababa";

  private String siteUrl = "https://ethiobuildconnect.et";

  /** Reads the reply-to mailbox over IMAP to notice replies and opt-outs. Optional. */
  private Imap imap = new Imap();

  @Getter
  @Setter
  public static class Imap {
    private String host = "";
    private int port = 993;
    private String username = "";
    private String password = "";

    public boolean configured() {
      return !host.isBlank() && !username.isBlank() && !password.isBlank();
    }
  }
}
