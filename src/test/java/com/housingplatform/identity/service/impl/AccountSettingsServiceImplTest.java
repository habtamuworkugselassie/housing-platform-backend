package com.housingplatform.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.ChangePasswordRequest;
import com.housingplatform.identity.dto.PaymentPreferencesRequest;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.UserMapper;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.shared.exception.BusinessException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountSettingsServiceImplTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserMapper userMapper;

  @InjectMocks private AccountSettingsServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private User user;

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .id(userId)
            .passwordHash("old-hash")
            .firstName("Abebe")
            .lastName("Kebede")
            .status(User.UserStatus.ACTIVE)
            .roles(new HashSet<>(Set.of(User.UserRole.BUYER)))
            .build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(passwordEncoder.encode("NewSecret1")).thenReturn("new-hash");
  }

  private static ChangePasswordRequest change(String current) {
    ChangePasswordRequest r = new ChangePasswordRequest();
    r.setCurrentPassword(current);
    r.setNewPassword("NewSecret1");
    return r;
  }

  @Test
  void aPasswordlessBuyerSetsAFirstPasswordWithoutACurrentOne() {
    user.setPasswordSet(false);

    service.changePassword(userId, change(null));

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(user.getPasswordSet()).isTrue();
  }

  @Test
  void aKnownPasswordMustBeConfirmed() {
    user.setPasswordSet(true);
    when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);
    when(passwordEncoder.matches("Right123", "old-hash")).thenReturn(true);

    assertThatThrownBy(() -> service.changePassword(userId, change("wrong")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("current password");
    assertThatThrownBy(() -> service.changePassword(userId, change(null)))
        .isInstanceOf(BusinessException.class);
    assertThat(user.getPasswordHash()).isEqualTo("old-hash");

    service.changePassword(userId, change("Right123"));
    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
  }

  @Test
  void anOlderAccountOfUnknownStateNeedsConfirmationUnlessItIsBuyerOnly() {
    user.setPasswordSet(null);
    assertThat(AccountSettingsServiceImpl.needsCurrentPassword(user)).isFalse();

    user.getRoles().add(User.UserRole.REALTOR);
    assertThat(AccountSettingsServiceImpl.needsCurrentPassword(user)).isTrue();

    user.setRoles(new HashSet<>(Set.of(User.UserRole.ADMIN)));
    assertThatThrownBy(() -> service.changePassword(userId, change(null)))
        .isInstanceOf(BusinessException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void paymentPreferencesStoreTheMethodAndANormalisedWalletNumber() {
    PaymentPreferencesRequest r = new PaymentPreferencesRequest();
    r.setPreferredPaymentMethod(ChapaPaymentMethod.TELEBIRR);
    r.setPaymentPhone("0911 22 33 44");

    service.updatePaymentPreferences(userId, r);

    assertThat(user.getPreferredPaymentMethod()).isEqualTo(ChapaPaymentMethod.TELEBIRR);
    assertThat(user.getPaymentPhone()).isEqualTo("+251911223344");

    r.setPreferredPaymentMethod(null);
    r.setPaymentPhone(" ");
    service.updatePaymentPreferences(userId, r);
    assertThat(user.getPreferredPaymentMethod()).isNull();
    assertThat(user.getPaymentPhone()).isNull();
  }

  @Test
  void aMalformedWalletNumberIsRefused() {
    PaymentPreferencesRequest r = new PaymentPreferencesRequest();
    r.setPaymentPhone("12");

    assertThatThrownBy(() -> service.updatePaymentPreferences(userId, r))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("09");
  }
}
