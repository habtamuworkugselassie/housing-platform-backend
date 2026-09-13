package com.housingplatform.exhibition.email;

/** A composed mail, ready to hand to the mail sender. */
public record ExhibitionEmailContent(String subject, String body) {}
