package com.housingplatform.shared.sms;

/** Sends one plain-text SMS; false when SMS is not configured or delivery failed (logged). */
public interface SmsSender {

  boolean configured();

  /**
   * @param phoneNumber E.164 number, e.g. {@code +251911223344}
   * @param text message body; keep it ASCII and under 160 characters to fit one SMS
   */
  boolean send(String phoneNumber, String text);
}
