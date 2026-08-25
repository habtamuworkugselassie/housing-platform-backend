package com.housingplatform.shared.security;

/**
 * Portal-specific scopes for OAuth2 access control Each portal has its own scope to ensure portal
 * isolation
 */
public class PortalScope {

  public static final String BUYER = "buyer";
  public static final String BANKER = "banker";
  public static final String REALTOR = "realtor";
  public static final String SUPPLIER = "supplier";
  public static final String ADMIN = "admin";
  public static final String SUPER_ADMIN = "super_admin";

  private PortalScope() {
    // Utility class
  }
}
