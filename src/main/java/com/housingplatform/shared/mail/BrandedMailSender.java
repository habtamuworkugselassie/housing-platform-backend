package com.housingplatform.shared.mail;

/** Sends a {@link BrandedMail}; false when mail is not configured or sending failed (logged). */
public interface BrandedMailSender {

  boolean configured();

  boolean send(BrandedMail mail);
}
