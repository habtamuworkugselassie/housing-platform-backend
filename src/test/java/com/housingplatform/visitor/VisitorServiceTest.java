package com.housingplatform.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.visitor.domain.VisitorRegistration;
import com.housingplatform.visitor.dto.VisitorDtos.Badge;
import com.housingplatform.visitor.dto.VisitorDtos.RegisterRequest;
import com.housingplatform.visitor.repository.VisitorRegistrationRepository;
import com.housingplatform.visitor.service.VisitorBadgeMailService;
import com.housingplatform.visitor.service.VisitorService;
import com.housingplatform.visitor.util.QrCodes;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VisitorServiceTest {

  private final VisitorRegistrationRepository repo = mock(VisitorRegistrationRepository.class);
  private final VisitorBadgeMailService mail = mock(VisitorBadgeMailService.class);
  private final VisitorService service = new VisitorService(repo, mail);

  private RegisterRequest request() {
    RegisterRequest r = new RegisterRequest();
    r.setFullName("Sara Tesfaye");
    r.setEmail("sara@example.com");
    r.setVisitorType("homebuyer");
    r.setLocale("am");
    return r;
  }

  @Test
  void registersWithAFreshUnambiguousCodeAndEmailsTheBadge() {
    when(repo.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
    when(repo.existsByCodeIgnoreCase(anyString())).thenReturn(false);
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              VisitorRegistration v = inv.getArgument(0);
              v.setId(UUID.randomUUID());
              return v;
            });
    when(mail.badgeUrl(any())).thenReturn("https://ethiobuildconnect.et/am/badge/EBC-ABC234");

    Badge b = service.register(request());

    assertThat(b.getCode()).matches("EBC-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    assertThat(b.getVisitorType()).isEqualTo("HOMEBUYER");
    assertThat(b.getLocale()).isEqualTo("am");
    assertThat(b.isAlreadyRegistered()).isFalse();
    ArgumentCaptor<VisitorRegistration> c = ArgumentCaptor.forClass(VisitorRegistration.class);
    verify(mail).sendBadge(c.capture());
    assertThat(c.getValue().getEmail()).isEqualTo("sara@example.com");
  }

  @Test
  void sameEmailKeepsItsCodeAndResendsInsteadOfDuplicating() {
    VisitorRegistration existing =
        VisitorRegistration.builder()
            .code("EBC-QWERTY")
            .fullName("Old")
            .email("sara@example.com")
            .visitorType(VisitorRegistration.VisitorType.OTHER)
            .locale("en")
            .status(VisitorRegistration.Status.REGISTERED)
            .build();
    when(repo.findByEmailIgnoreCase("sara@example.com")).thenReturn(Optional.of(existing));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(mail.badgeUrl(any())).thenReturn("https://x/badge/EBC-QWERTY");

    Badge b = service.register(request());

    assertThat(b.getCode()).isEqualTo("EBC-QWERTY");
    assertThat(b.isAlreadyRegistered()).isTrue();
    assertThat(existing.getFullName()).isEqualTo("Sara Tesfaye");
    verify(mail).sendBadge(existing);
  }

  @Test
  void honeypotStoresNothing() {
    RegisterRequest r = request();
    r.setWebsite("http://spam");
    service.register(r);
    verify(repo, never()).save(any());
    verify(mail, never()).sendBadge(any());
  }

  @Test
  void checkInAcceptsScannedUrlAndRefusesASecondTime() {
    VisitorRegistration v =
        VisitorRegistration.builder()
            .code("EBC-ABC234")
            .fullName("Sara")
            .email("s@x")
            .visitorType(VisitorRegistration.VisitorType.OTHER)
            .locale("en")
            .status(VisitorRegistration.Status.REGISTERED)
            .build();
    when(repo.findByCodeIgnoreCase("EBC-ABC234")).thenReturn(Optional.of(v));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.checkIn("https://ethiobuildconnect.et/am/badge/ebc-abc234", "door@ebc");
    assertThat(v.getStatus()).isEqualTo(VisitorRegistration.Status.CHECKED_IN);
    assertThat(v.getCheckedInAt()).isBeforeOrEqualTo(LocalDateTime.now());
    assertThat(v.getCheckedInBy()).isEqualTo("door@ebc");

    assertThatThrownBy(() -> service.checkIn("abc234", "door@ebc"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Already checked in");
  }

  @Test
  void codeExtractionIsForgiving() {
    assertThat(VisitorService.extractCode(" ebc-abc234 ")).isEqualTo("EBC-ABC234");
    assertThat(VisitorService.extractCode("ABC 234")).isEqualTo("EBC-ABC234");
    assertThatThrownBy(() -> VisitorService.extractCode("hello"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void qrIsAPng() {
    byte[] png = QrCodes.png("https://ethiobuildconnect.et/badge/EBC-ABC234", 200);
    assertThat(png).hasSizeGreaterThan(100);
    assertThat(png[1]).isEqualTo((byte) 'P');
    assertThat(png[2]).isEqualTo((byte) 'N');
  }
}
