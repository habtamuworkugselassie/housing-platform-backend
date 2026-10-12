package com.housingplatform.identity.domain;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseAuditEntity {

  /** Optional since quick registration: buyers may sign up with a phone number only. */
  @Column(unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  private String phoneNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Column(nullable = false)
  @Builder.Default
  private Boolean emailVerified = false;

  @Column(nullable = false)
  @Builder.Default
  private Boolean phoneVerified = false;

  /**
   * Whether the user has chosen a password: false for passwordless sign-ups (quick registration,
   * Google), true once one is set; null for older accounts where it was never recorded.
   */
  private Boolean passwordSet;

  /** Method preselected at checkout. Chapa hosts the payment, so no card or wallet data is kept. */
  @Enumerated(EnumType.STRING)
  private ChapaPaymentMethod preferredPaymentMethod;

  /** Mobile-money number (E.164) handed to Chapa for wallet payments. */
  private String paymentPhone;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role")
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Set<UserRole> roles = new HashSet<>();

  public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION,
    /** Self-deleted: PII scrubbed, account closed and unrecoverable. */
    DELETED
  }

  public enum UserRole {
    BUYER,
    BANKER,
    REALTOR,
    SUPPLIER,
    ADMIN,
    /** Platform owner tier: manages admins and sponsor-company credentials. */
    SUPER_ADMIN;

    /** Roles that may only be granted by a {@link #SUPER_ADMIN}. */
    public boolean isPrivileged() {
      return this == ADMIN || this == SUPER_ADMIN;
    }
  }
}
