package com.housingplatform.identity.domain;

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

  @Column(nullable = false, unique = true)
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
    PENDING_VERIFICATION
  }

  public enum UserRole {
    BUYER,
    BANKER,
    REALTOR,
    SUPPLIER,
    ADMIN
  }
}
