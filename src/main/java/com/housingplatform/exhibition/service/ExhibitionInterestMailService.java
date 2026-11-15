package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.domain.ExhibitionInterest;

/**
 * Emails that follow an exhibition interest registration: a receipt to the registrant and a notice
 * to the team.
 */
public interface ExhibitionInterestMailService {
  void sendInterestEmails(ExhibitionInterest interest);
}
