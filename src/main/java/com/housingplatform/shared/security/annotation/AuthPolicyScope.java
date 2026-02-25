package com.housingplatform.shared.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthPolicyScope {

  Policy value();

  enum Policy {
    /** No authentication required */
    UNSECURED,

    /** Requires authentication with any valid scope */
    AUTHENTICATED,

    /** Requires buyer scope */
    BUYER_SECURED,

    /** Requires banker scope */
    BANKER_SECURED,

    /** Requires realtor scope */
    REALTOR_SECURED,

    /** Requires supplier scope */
    SUPPLIER_SECURED,

    /** Requires admin scope */
    ADMIN_SECURED
  }
}
