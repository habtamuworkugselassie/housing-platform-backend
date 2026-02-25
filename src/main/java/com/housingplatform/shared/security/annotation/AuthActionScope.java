package com.housingplatform.shared.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthActionScope {

  /**
   * The action/scope required for this endpoint Examples: "properties.read", "properties.write",
   * "loans.approve", etc.
   */
  String value();
}
