package com.housingplatform.publicsupport.rag;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.identity.service.OrganizationPublicVisibility;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.repository.ReviewRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportRagIndexingService {

  private final OrganizationRepository organizationRepository;
  private final PropertyRepository propertyRepository;
  private final SponsorshipRepository sponsorshipRepository;
  private final ReviewRepository reviewRepository;
  private final SupportRagVectorStore vectorStore;
  private final SupportRagEmbeddingClient embeddingClient;

  @Value("${app.support-chat.rag-vector-enabled:false}")
  private boolean ragVectorEnabled;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  public boolean isEnabled() {
    return ragVectorEnabled && embeddingClient.isConfigured();
  }

  @Transactional
  public void indexOrganization(UUID organizationId) {
    if (!isEnabled()) {
      return;
    }
    List<Organization> withContact =
        organizationRepository.findAllByIdInWithContact(List.of(organizationId));
    Organization org =
        withContact.isEmpty()
            ? organizationRepository.findById(organizationId).orElse(null)
            : withContact.get(0);
    if (org == null) {
      vectorStore.delete(SupportRagSourceType.ORGANIZATION, organizationId);
      return;
    }
    if (!OrganizationPublicVisibility.isPubliclyListed(org)) {
      vectorStore.delete(SupportRagSourceType.ORGANIZATION, organizationId);
      return;
    }
    String content = buildOrganizationText(org);
    upsertSingle(SupportRagSourceType.ORGANIZATION, organizationId, content);
  }

  @Transactional
  public void indexProperty(UUID propertyId) {
    if (!isEnabled()) {
      return;
    }
    Property p = propertyRepository.findById(propertyId).orElse(null);
    if (p == null) {
      vectorStore.delete(SupportRagSourceType.PROPERTY, propertyId);
      return;
    }
    if (p.getStatus() != Property.PropertyStatus.AVAILABLE
        || p.getVerificationStatus() == Property.VerificationStatus.REJECTED) {
      vectorStore.delete(SupportRagSourceType.PROPERTY, propertyId);
      return;
    }
    if (p.getRealEstateCompanyId() != null) {
      Organization company =
          organizationRepository.findById(p.getRealEstateCompanyId()).orElse(null);
      if (company == null || !OrganizationPublicVisibility.isPubliclyListed(company)) {
        vectorStore.delete(SupportRagSourceType.PROPERTY, propertyId);
        return;
      }
    }
    String content = buildPropertyText(p);
    upsertSingle(SupportRagSourceType.PROPERTY, propertyId, content);
  }

  @Transactional
  public void indexSponsorship(UUID sponsorshipId) {
    if (!isEnabled()) {
      return;
    }
    Sponsorship s = sponsorshipRepository.findById(sponsorshipId).orElse(null);
    if (s == null) {
      vectorStore.delete(SupportRagSourceType.SPONSORSHIP, sponsorshipId);
      return;
    }
    if (s.getStatus() != Sponsorship.SponsorshipStatus.ACTIVE) {
      vectorStore.delete(SupportRagSourceType.SPONSORSHIP, sponsorshipId);
      return;
    }
    String content = buildSponsorshipText(s);
    upsertSingle(SupportRagSourceType.SPONSORSHIP, sponsorshipId, content);
  }

  public void delete(SupportRagSourceType type, UUID sourceId) {
    vectorStore.delete(type, sourceId);
  }

  private void upsertSingle(SupportRagSourceType type, UUID sourceId, String content) {
    String hash = sha256Hex(content);
    String existing = vectorStore.findContentHash(type, sourceId, 0);
    if (hash.equals(existing)) {
      return;
    }
    float[] emb = embeddingClient.embed(content);
    if (emb.length == 0) {
      log.warn("Skipping RAG index: empty embedding for {} {}", type, sourceId);
      return;
    }
    vectorStore.upsert(UUID.randomUUID(), type, sourceId, 0, content, hash, emb);
  }

  private String buildOrganizationText(Organization o) {
    Double avg = reviewRepository.getAverageRatingForOrganization(o.getId());
    Integer cnt = reviewRepository.getReviewCountForOrganization(o.getId());
    String avgStr = avg != null ? String.format(java.util.Locale.ROOT, "%.2f", avg) : "none";
    String countStr = cnt != null ? cnt.toString() : "0";
    String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
    String profile = base + "/organizations/" + o.getId();
    StringBuilder sb = new StringBuilder();
    sb.append("ORGANIZATION marketplace profile: name=").append(nullToEmpty(o.getName()));
    sb.append(" | orgType=").append(o.getType());
    sb.append(" | status=").append(o.getStatus());
    sb.append(" | verificationLevel=").append(o.getVerificationLevel());
    sb.append(" | city=").append(nullToEmpty(o.getCity()));
    sb.append(" | address=").append(truncate(nullToEmpty(o.getAddress()), 200));
    sb.append(" | description=").append(truncate(nullToEmpty(o.getDescription()), 400));
    sb.append(" | avgOrgReview=").append(avgStr).append(" | orgReviewCount=").append(countStr);
    OrganizationContact c = o.getContact();
    if (c != null) {
      sb.append(" | email=").append(nullToEmpty(c.getEmail()));
      sb.append(" | website=").append(nullToEmpty(c.getWebsite()));
      String phones = formatPhones(c);
      if (StringUtils.hasText(phones)) {
        sb.append(" | phones=").append(phones);
      }
    }
    sb.append(" | profileUrl=").append(profile);
    return sb.toString();
  }

  private String buildPropertyText(Property p) {
    Double avg = reviewRepository.getAverageRatingForProperty(p.getId());
    Integer cnt = reviewRepository.getReviewCountForProperty(p.getId());
    String avgStr = avg != null ? String.format(java.util.Locale.ROOT, "%.2f", avg) : "none";
    String countStr = cnt != null ? cnt.toString() : "0";
    String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
    String url = base + "/properties/" + p.getId();
    StringBuilder sb = new StringBuilder();
    sb.append("PROPERTY listing: title=").append(nullToEmpty(p.getTitle()));
    sb.append(" | city=").append(nullToEmpty(p.getCity()));
    sb.append(" | listingVerification=").append(p.getVerificationStatus());
    sb.append(" | status=").append(p.getStatus());
    if (p.getPriceETB() != null) {
      sb.append(" | priceETB=").append(p.getPriceETB().stripTrailingZeros().toPlainString());
    }
    sb.append(" | category=").append(p.getCategory());
    sb.append(" | type=").append(p.getType());
    sb.append(" | avgPropertyReview=")
        .append(avgStr)
        .append(" | propertyReviewCount=")
        .append(countStr);
    if (p.getRealEstateCompanyId() != null) {
      organizationRepository
          .findById(p.getRealEstateCompanyId())
          .ifPresent(org -> sb.append(" | listingCompany=").append(nullToEmpty(org.getName())));
    }
    sb.append(" | propertyUrl=").append(url);
    return sb.toString();
  }

  private String buildSponsorshipText(Sponsorship s) {
    StringBuilder sb = new StringBuilder();
    sb.append("EXHIBITION sponsorship package: name=").append(nullToEmpty(s.getName()));
    sb.append(" | tierType=").append(s.getType());
    sb.append(" | status=").append(s.getStatus());
    if (s.getBasePrice() != null) {
      sb.append(" | basePriceETB=").append(s.getBasePrice().stripTrailingZeros().toPlainString());
    }
    sb.append(" | description=").append(truncate(nullToEmpty(s.getDescription()), 500));
    sb.append(" | features=").append(truncate(nullToEmpty(s.getFeatures()), 500));
    sb.append(" | packageId=").append(s.getId());
    return sb.toString();
  }

  private static String formatPhones(OrganizationContact c) {
    if (c.getPhones() == null || c.getPhones().isEmpty()) {
      return "";
    }
    return c.getPhones().stream()
        .sorted(
            Comparator.comparing(
                OrganizationPhone::getDisplayOrder, Comparator.nullsFirst(Integer::compareTo)))
        .map(
            p -> {
              String cc = p.getCountryCode() != null ? p.getCountryCode().trim() : "";
              String n = p.getNumber() != null ? p.getNumber().trim() : "";
              return (cc + n).trim();
            })
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining(", "));
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String truncate(String s, int max) {
    if (s.length() <= max) {
      return s.replace('\n', ' ');
    }
    return s.substring(0, max).replace('\n', ' ') + "…";
  }

  private static String sha256Hex(String content) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      return String.valueOf(Objects.hash(content));
    }
  }
}
