package com.housingplatform.exhibition.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.identity.domain.Sponsorship;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * These assertions are about the promises the copy makes, not its prose.
 *
 * <p>Three of them are worth stating plainly, because getting any of them wrong is the kind of
 * mistake that is only discovered by a recipient: every mail must carry the event's date and venue
 * (a reminder that does not say when is not a reminder); every reminder must carry a working way
 * out; and no mail may claim a number the site does not publish.
 */
class ExhibitionEmailComposerTest {

  private ExpoProperties expo;
  private ExhibitionEmailComposer composer;

  @BeforeEach
  void setUp() {
    expo = new ExpoProperties();
    composer = new ExhibitionEmailComposer(expo);
    ReflectionTestUtils.setField(composer, "frontendBaseUrl", "https://ethiobuildconnect.et/");
  }

  private static ExhibitionInterest interest(String type) {
    return ExhibitionInterest.builder()
        .email("lead@example.com")
        .interestType(type)
        .unsubscribeToken("tok-123")
        .build();
  }

  @ParameterizedTest
  @EnumSource(ExhibitionEmailKind.class)
  void everyMailNamesTheDatesAndTheVenue(ExhibitionEmailKind kind) {
    ExhibitionEmailContent mail = composer.compose(interest("visitor"), kind);

    assertThat(mail.subject()).isNotBlank();
    assertThat(mail.body()).contains("16-18 November 2026");
    assertThat(mail.body()).contains("Addis Convention Center", "Addis Ababa");
  }

  @ParameterizedTest
  @EnumSource(
      value = ExhibitionEmailKind.class,
      names = {"REMINDER_T30", "REMINDER_T7", "REMINDER_T1"})
  void everyReminderCarriesAWayOut(ExhibitionEmailKind kind) {
    ExhibitionEmailContent mail = composer.compose(interest("visitor"), kind);

    assertThat(mail.body())
        .contains(
            "https://ethiobuildconnect.et/api/v1/exhibition/interest/unsubscribe?token=tok-123");
  }

  @Test
  void theConfirmationDoesNotOfferToUnsubscribe() {
    // It answers something the reader did seconds ago. Offering an opt-out on a receipt reads as
    // though we expect them to regret it, and the reminder series is where the opt-out belongs.
    ExhibitionEmailContent mail =
        composer.compose(interest("visitor"), ExhibitionEmailKind.CONFIRMATION);

    assertThat(mail.body()).doesNotContain("unsubscribe");
  }

  @ParameterizedTest
  @ValueSource(strings = {"visitor", "exhibitor", "partner"})
  void theConfirmationSaysWhatHappensNext(String type) {
    ExhibitionEmailContent mail =
        composer.compose(interest(type), ExhibitionEmailKind.CONFIRMATION);

    assertThat(mail.body()).contains("What happens next");
  }

  @Test
  void exhibitorAndVisitorConfirmationsAreNotTheSameMail() {
    String visitor = composer.compose(interest("visitor"), ExhibitionEmailKind.CONFIRMATION).body();
    String exhibitor =
        composer.compose(interest("exhibitor"), ExhibitionEmailKind.CONFIRMATION).body();
    String partner = composer.compose(interest("partner"), ExhibitionEmailKind.CONFIRMATION).body();

    assertThat(visitor).isNotEqualTo(exhibitor).isNotEqualTo(partner);
    assertThat(exhibitor).isNotEqualTo(partner);
    // An exhibitor is told when a person will contact them; that commitment is the whole point of
    // the mail, and a visitor must not be given it, because nobody is going to call them.
    assertThat(exhibitor).contains("two working days");
    assertThat(visitor).doesNotContain("working days");
  }

  @Test
  void anExhibitorIsRemindedWhichPackageTheyAskedAbout() {
    ExhibitionInterest withPackage = interest("exhibitor");
    withPackage.setSponsorship(Sponsorship.builder().name("Gold Partner").build());

    ExhibitionEmailContent mail = composer.compose(withPackage, ExhibitionEmailKind.CONFIRMATION);

    assertThat(mail.body()).contains("Gold Partner");
  }

  @Test
  void aMissingPackageLeavesNoEmptyLabelBehind() {
    ExhibitionEmailContent mail =
        composer.compose(interest("exhibitor"), ExhibitionEmailKind.CONFIRMATION);

    assertThat(mail.body()).doesNotContain("Package you asked about");
  }

  @ParameterizedTest
  @EnumSource(ExhibitionEmailKind.class)
  void siteLinksAreTaggedSoEmailTrafficIsNotCountedAsDirect(ExhibitionEmailKind kind) {
    ExhibitionEmailContent mail = composer.compose(interest("visitor"), kind);

    assertThat(mail.body())
        .contains("utm_source=lifecycle&utm_medium=email&utm_campaign=expo-2026&utm_content=")
        .contains("utm_content=" + kind.name().toLowerCase());
  }

  @Test
  void noMailClaimsAttendanceOrPricingTheSiteDoesNotPublish() {
    // Same rule the ad copy is held to: a claim has to be substantiated on the landing page, and
    // the site states no visitor numbers, no exhibitor counts and no admission price.
    for (ExhibitionEmailKind kind : ExhibitionEmailKind.values()) {
      for (String type : new String[] {"visitor", "exhibitor", "partner"}) {
        String body = composer.compose(interest(type), kind).body().toLowerCase();
        assertThat(body)
            .as("%s / %s", kind, type)
            .doesNotContain("free entry")
            .doesNotContain("free admission")
            .doesNotContain("exhibitors expected")
            .doesNotContain("visitors expected");
      }
    }
  }

  @Test
  void anUnknownInterestTypeIsTreatedAsAVisitorRatherThanFailing() {
    // The column is free text and predates the enum-like validation on the request, so a row
    // written by an older client must still produce a sendable mail.
    ExhibitionInterest odd = interest("VISITOR ");
    assertThat(composer.compose(odd, ExhibitionEmailKind.CONFIRMATION).body())
        .isEqualTo(composer.compose(interest("visitor"), ExhibitionEmailKind.CONFIRMATION).body());

    ExhibitionInterest nullType = interest(null);
    assertThat(composer.compose(nullType, ExhibitionEmailKind.CONFIRMATION).body()).isNotBlank();
  }

  @Test
  void aSingleDayEventReadsAsOneDateAndASpanAcrossMonthsReadsInFull() {
    expo.setStartDate(LocalDate.of(2026, 11, 16));
    expo.setEndDate(LocalDate.of(2026, 11, 16));
    assertThat(composer.dateRange()).isEqualTo("16 November 2026");

    expo.setStartDate(LocalDate.of(2026, 10, 30));
    expo.setEndDate(LocalDate.of(2026, 11, 2));
    assertThat(composer.dateRange()).isEqualTo("30 October 2026 - 2 November 2026");
  }

  @Test
  void movingTheExpoMovesEveryMailWithIt() {
    expo.setStartDate(LocalDate.of(2027, 3, 4));
    expo.setEndDate(LocalDate.of(2027, 3, 6));
    expo.setVenue("Millennium Hall");

    String body = composer.compose(interest("visitor"), ExhibitionEmailKind.REMINDER_T7).body();

    assertThat(body).contains("4-6 March 2027", "Millennium Hall");
    assertThat(body).doesNotContain("November 2026");
  }
}
