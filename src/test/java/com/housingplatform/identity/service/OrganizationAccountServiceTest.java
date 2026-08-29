package com.housingplatform.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.CreateOrganizationAccountRequest;
import com.housingplatform.identity.dto.OrganizationAccountResponse;
import com.housingplatform.identity.dto.SetAccountPasswordRequest;
import com.housingplatform.identity.dto.UpdateAccountStatusRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.impl.OrganizationAccountServiceImpl;
import com.housingplatform.shared.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationAccountServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private RealEstateAgentRepository realEstateAgentRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private PasswordResetEmailService passwordResetEmailService;

  @InjectMocks private OrganizationAccountServiceImpl service;

  private UUID orgId;

  @BeforeEach
  void setUp() {
    orgId = UUID.randomUUID();
    when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              if (u.getId() == null) {
                u.setId(UUID.randomUUID());
              }
              return u;
            });
    when(organizationRepository.save(any(Organization.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private Organization org(Organization.OrganizationType type) {
    Organization o =
        Organization.builder()
            .name("Acme Sponsor")
            .type(type)
            .status(Organization.OrganizationStatus.APPROVED)
            .build();
    o.setId(orgId);
    when(organizationRepository.findById(orgId)).thenReturn(Optional.of(o));
    return o;
  }

  private CreateOrganizationAccountRequest createRequest() {
    CreateOrganizationAccountRequest r = new CreateOrganizationAccountRequest();
    r.setEmail("  Ops@Acme.COM ");
    r.setPassword("Password1");
    r.setFirstName(" Ada ");
    r.setLastName(" Lovelace ");
    return r;
  }

  private User memberOf(Organization o, User.UserStatus status) {
    User u =
        User.builder()
            .email("staff@acme.com")
            .passwordHash("old")
            .firstName("Staff")
            .lastName("Member")
            .status(status)
            .organization(o)
            .roles(new java.util.HashSet<>(List.of(User.UserRole.SUPPLIER)))
            .build();
    u.setId(UUID.randomUUID());
    when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
    return u;
  }

  // --- create --------------------------------------------------------------

  @Test
  void createsLoginForASponsorTypeThatHasNoPortalOfItsOwn() {
    Organization o = org(Organization.OrganizationType.MEDIA_COMPANY);

    OrganizationAccountResponse response = service.createAccount(orgId, createRequest());

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    User user = saved.getValue();

    // A media company is still a sponsor company: it must get a working login.
    assertEquals(List.of(User.UserRole.SUPPLIER), List.copyOf(user.getRoles()));
    assertEquals("ops@acme.com", user.getEmail(), "email is normalized");
    assertEquals("Ada", user.getFirstName());
    assertEquals("Lovelace", user.getLastName());
    assertEquals("hashed:Password1", user.getPasswordHash(), "password must never be stored raw");
    assertEquals(User.UserStatus.ACTIVE, user.getStatus());
    assertTrue(response.getPrimaryContact());
    assertSame(user, o.getPrimaryContact());
  }

  @Test
  void createAccountSendsAWelcomeEmailWithASetPasswordLink() {
    org(Organization.OrganizationType.MEDIA_COMPANY);

    service.createAccount(orgId, createRequest());

    verify(passwordResetTokenRepository).save(any());
    verify(passwordResetEmailService).sendAccountWelcomeEmail(anyString(), anyString(), any());
  }

  @Test
  void firstAccountBecomesPrimaryContactWithoutBeingAsked() {
    Organization o = org(Organization.OrganizationType.BANK);
    CreateOrganizationAccountRequest request = createRequest();
    request.setMakePrimaryContact(null);

    OrganizationAccountResponse response = service.createAccount(orgId, request);

    assertTrue(response.getPrimaryContact());
    assertNotNull(o.getPrimaryContact());
  }

  @Test
  void additionalAccountDoesNotStealPrimaryContact() {
    Organization o = org(Organization.OrganizationType.BANK);
    User incumbent = memberOf(o, User.UserStatus.ACTIVE);
    o.setPrimaryContact(incumbent);

    CreateOrganizationAccountRequest request = createRequest();
    request.setMakePrimaryContact(null);
    OrganizationAccountResponse response = service.createAccount(orgId, request);

    assertFalse(response.getPrimaryContact());
    assertSame(incumbent, o.getPrimaryContact());
  }

  @Test
  void rejectsAnEmailThatAlreadyBelongsToSomeone() {
    org(Organization.OrganizationType.SUPPLIER);
    when(userRepository.existsByEmail("ops@acme.com")).thenReturn(true);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.createAccount(orgId, createRequest()));
    assertTrue(ex.getMessage().contains("already exists"));
    verify(userRepository, never()).save(any());
  }

  @Test
  void realEstateAccountGetsAnAgentRecordAsSuperAgent() {
    org(Organization.OrganizationType.REAL_ESTATE_COMPANY);
    when(realEstateAgentRepository.findByUserId(any())).thenReturn(Optional.empty());
    when(realEstateAgentRepository.findSuperAgentByOrganizationId(orgId))
        .thenReturn(Optional.empty());

    service.createAccount(orgId, createRequest());

    ArgumentCaptor<RealEstateAgent> agent = ArgumentCaptor.forClass(RealEstateAgent.class);
    verify(realEstateAgentRepository).save(agent.capture());
    assertTrue(agent.getValue().getIsSuperAgent());
  }

  @Test
  void promotingOnCreateDemotesTheIncumbentSuperAgent() {
    Organization o = org(Organization.OrganizationType.REAL_ESTATE_COMPANY);
    User incumbent = memberOf(o, User.UserStatus.ACTIVE);
    o.setPrimaryContact(incumbent);

    RealEstateAgent oldSuper =
        RealEstateAgent.builder().user(incumbent).organization(o).isSuperAgent(true).build();
    oldSuper.setId(UUID.randomUUID());
    when(realEstateAgentRepository.findSuperAgentByOrganizationId(orgId))
        .thenReturn(Optional.of(oldSuper));
    when(realEstateAgentRepository.findByUserId(any())).thenReturn(Optional.empty());

    CreateOrganizationAccountRequest request = createRequest();
    request.setMakePrimaryContact(true);
    service.createAccount(orgId, request);

    // Exactly one super agent per company, or listings show two owners.
    assertFalse(oldSuper.getIsSuperAgent());
  }

  // --- password ------------------------------------------------------------

  @Test
  void setPasswordHashesAndActivatesAPendingAccount() {
    Organization o = org(Organization.OrganizationType.SUPPLIER);
    User user = memberOf(o, User.UserStatus.PENDING_VERIFICATION);

    SetAccountPasswordRequest request = new SetAccountPasswordRequest();
    request.setPassword("Newpass1");
    OrganizationAccountResponse response = service.setPassword(orgId, user.getId(), request);

    assertEquals("hashed:Newpass1", user.getPasswordHash());
    // A handed-over password is useless if the account still cannot sign in.
    assertEquals(User.UserStatus.ACTIVE, user.getStatus());
    assertEquals(User.UserStatus.ACTIVE, response.getStatus());
  }

  @Test
  void setPasswordLeavesASuspendedAccountSuspended() {
    Organization o = org(Organization.OrganizationType.SUPPLIER);
    User user = memberOf(o, User.UserStatus.SUSPENDED);

    SetAccountPasswordRequest request = new SetAccountPasswordRequest();
    request.setPassword("Newpass1");
    service.setPassword(orgId, user.getId(), request);

    assertEquals(User.UserStatus.SUSPENDED, user.getStatus(), "reset must not lift a suspension");
  }

  @Test
  void refusesToTouchAnAccountFromAnotherCompany() {
    org(Organization.OrganizationType.SUPPLIER);
    Organization other =
        Organization.builder().name("Other").type(Organization.OrganizationType.BANK).build();
    other.setId(UUID.randomUUID());
    User outsider = memberOf(other, User.UserStatus.ACTIVE);

    SetAccountPasswordRequest request = new SetAccountPasswordRequest();
    request.setPassword("Newpass1");

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.setPassword(orgId, outsider.getId(), request));
    assertTrue(ex.getMessage().contains("does not belong"));
  }

  // --- status / primary / unlink -------------------------------------------

  @Test
  void disablingAnAccountAlsoDeactivatesItsAgentRecord() {
    Organization o = org(Organization.OrganizationType.REAL_ESTATE_COMPANY);
    User user = memberOf(o, User.UserStatus.ACTIVE);
    RealEstateAgent agent =
        RealEstateAgent.builder()
            .user(user)
            .organization(o)
            .status(RealEstateAgent.AgentStatus.ACTIVE)
            .isSuperAgent(false)
            .build();
    when(realEstateAgentRepository.findByUserId(user.getId())).thenReturn(Optional.of(agent));

    UpdateAccountStatusRequest request = new UpdateAccountStatusRequest();
    request.setStatus(User.UserStatus.INACTIVE);
    service.setStatus(orgId, user.getId(), request);

    assertEquals(User.UserStatus.INACTIVE, user.getStatus());
    assertEquals(RealEstateAgent.AgentStatus.INACTIVE, agent.getStatus());
  }

  @Test
  void onlyAnActiveAccountCanBecomePrimaryContact() {
    Organization o = org(Organization.OrganizationType.SUPPLIER);
    User user = memberOf(o, User.UserStatus.INACTIVE);

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.makePrimaryContact(orgId, user.getId()));
    assertTrue(ex.getMessage().contains("active"));
  }

  @Test
  void primaryContactCannotBeUnlinked() {
    Organization o = org(Organization.OrganizationType.SUPPLIER);
    User user = memberOf(o, User.UserStatus.ACTIVE);
    o.setPrimaryContact(user);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlinkAccount(orgId, user.getId()));
    assertTrue(ex.getMessage().contains("primary contact"));
    assertNotNull(user.getOrganization(), "the link must survive a refused unlink");
  }

  @Test
  void unlinkingClearsTheCompanyLinkButKeepsThePerson() {
    Organization o = org(Organization.OrganizationType.SUPPLIER);
    User user = memberOf(o, User.UserStatus.ACTIVE);
    when(realEstateAgentRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

    service.unlinkAccount(orgId, user.getId());

    assertNull(user.getOrganization());
    verify(userRepository, never()).delete(any(User.class));
  }

  @Test
  void listsPrimaryContactFirst() {
    Organization o = org(Organization.OrganizationType.SUPPLIER);
    User staff = memberOf(o, User.UserStatus.ACTIVE);
    User boss = memberOf(o, User.UserStatus.ACTIVE);
    o.setPrimaryContact(boss);
    when(userRepository.findByOrganizationId(orgId)).thenReturn(List.of(staff, boss));

    List<OrganizationAccountResponse> accounts = service.getAccounts(orgId);

    assertEquals(2, accounts.size());
    assertEquals(boss.getId(), accounts.get(0).getId());
    assertTrue(accounts.get(0).getPrimaryContact());
    assertFalse(accounts.get(1).getPrimaryContact());
  }
}
