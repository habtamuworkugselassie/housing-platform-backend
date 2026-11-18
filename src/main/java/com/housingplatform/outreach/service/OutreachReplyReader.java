package com.housingplatform.outreach.service;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Looks in the reply-to mailbox (IMAP, e.g. the expo Gmail with an app password) for messages from
 * a contact since we last wrote to them. IMAP (993) is reachable from the droplet; only the SMTP
 * ports are blocked there.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachReplyReader {

  public record Reply(String from, LocalDateTime receivedAt, String text) {}

  private final OutreachProperties props;

  public boolean configured() {
    return props.getImap().configured();
  }

  public List<Reply> repliesFrom(String contactEmail, LocalDateTime since) throws Exception {
    OutreachProperties.Imap imap = props.getImap();
    Properties p = new Properties();
    p.put("mail.store.protocol", "imaps");
    p.put("mail.imaps.host", imap.getHost());
    p.put("mail.imaps.port", String.valueOf(imap.getPort()));
    p.put("mail.imaps.connectiontimeout", "10000");
    p.put("mail.imaps.timeout", "15000");
    Session session = Session.getInstance(p);
    List<Reply> out = new ArrayList<>();
    try (Store store = session.getStore("imaps")) {
      store.connect(imap.getHost(), imap.getPort(), imap.getUsername(), imap.getPassword());
      try (Folder inbox = store.getFolder("INBOX")) {
        inbox.open(Folder.READ_ONLY);
        Date sinceDate = Date.from(since.minusDays(1).atZone(ZoneId.systemDefault()).toInstant());
        SearchTerm term =
            new AndTerm(
                new FromStringTerm(contactEmail),
                new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));
        for (Message m : inbox.search(term)) {
          Date received = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
          LocalDateTime at =
              received == null
                  ? LocalDateTime.now()
                  : LocalDateTime.ofInstant(received.toInstant(), ZoneId.systemDefault());
          if (at.isBefore(since)) continue;
          String from =
              m.getFrom() != null && m.getFrom().length > 0
                  ? ((InternetAddress) m.getFrom()[0]).getAddress()
                  : "";
          out.add(new Reply(from, at, text(m)));
        }
      }
    }
    return out;
  }

  private static String text(Part part) {
    try {
      if (part.isMimeType("text/plain")) return String.valueOf(part.getContent());
      if (part.isMimeType("multipart/*")) {
        Multipart mp = (Multipart) part.getContent();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mp.getCount(); i++) sb.append(text(mp.getBodyPart(i))).append('\n');
        return sb.toString();
      }
      if (part.isMimeType("text/html")) {
        return String.valueOf(part.getContent()).replaceAll("<[^>]+>", " ");
      }
    } catch (Exception e) {
      log.debug("Could not read a reply part: {}", e.getMessage());
    }
    return "";
  }
}
