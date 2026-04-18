package com.housingplatform.publicsupport;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.dto.SponsorshipResponse;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.service.OrganizationPublicVisibility;
import com.housingplatform.identity.service.SponsorshipService;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.repository.ReviewRepository;
import com.housingplatform.publicsupport.rag.SupportRagVectorRetrievalService;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Retrieval layer for public support chat: pulls live snippets from properties, organizations (all
 * marketplace types when relevant), optional ranked directory sections, and active exhibition
 * sponsorship packages so the LLM can ground answers in current DB state. Optional pgvector
 * semantic index (see VECTOR_RAG_HITS) reduces repeated SQL when enabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportChatRagContextService {

  private final PropertyRepository propertyRepository;
  private final OrganizationRepository organizationRepository;
  private final ReviewRepository reviewRepository;
  private final SponsorshipService sponsorshipService;
  private final SupportRagVectorRetrievalService vectorRetrieval;

  @Value("${app.support-chat.rag-enabled:true}")
  private boolean ragEnabled;

  @Value("${app.support-chat.rag-max-properties:12}")
  private int maxProperties;

  @Value("${app.support-chat.rag-max-organizations:8}")
  private int maxOrganizations;

  @Value("${app.support-chat.rag-max-chars:4500}")
  private int maxChars;

  @Value("${app.support-chat.rag-include-sponsorship-packages:true}")
  private boolean includeSponsorshipPackages;

  @Value("${app.support-chat.rag-include-marketplace-directory:true}")
  private boolean includeMarketplaceDirectory;

  @Value("${app.support-chat.rag-max-directory-organizations:15}")
  private int maxDirectoryOrganizations;

  @Value("${app.support-chat.rag-max-directory-properties:15}")
  private int maxDirectoryProperties;

  @Value("${app.support-chat.rag-directory-property-candidate-pool:400}")
  private int directoryPropertyCandidatePool;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Value("${app.support-chat.rag-vector-replaces-lexical-rag:true}")
  private boolean ragVectorReplacesLexicalRag;

  @Transactional(readOnly = true)
  public String buildContextBlock(String userMessage) {
    if (!ragEnabled || !StringUtils.hasText(userMessage)) {
      return "";
    }
    try {
      String vectorBlock =
          vectorRetrieval.isEnabled() ? vectorRetrieval.retrieveFormatted(userMessage) : "";

      if (vectorRetrieval.isEnabled()
          && ragVectorReplacesLexicalRag
          && StringUtils.hasText(vectorBlock)) {
        StringBuilder body = new StringBuilder(vectorBlock);
        boolean hasContent = true;
        if (includeSponsorshipPackages) {
          hasContent |= appendSponsorshipPackagesSection(body);
        }
        if (!hasContent) {
          return "";
        }
        return finalizeRagBlock(body);
      }

      StringBuilder body = new StringBuilder();
      boolean hasContent = false;

      if (StringUtils.hasText(vectorBlock)) {
        body.append(vectorBlock).append("\n\n");
        hasContent = true;
      }

      List<String> tokens = extractSearchTokens(userMessage);
      if (!tokens.isEmpty()) {
        List<Property> properties = findMatchingProperties(tokens);
        List<Organization> organizations = findMatchingOrganizations(userMessage.trim());
        if (!properties.isEmpty()) {
          appendPropertiesSection(body, properties);
          hasContent = true;
        }
        if (!organizations.isEmpty()) {
          appendOrganizationsSection(body, organizations);
          hasContent = true;
        }
      }

      if (includeMarketplaceDirectory) {
        hasContent |= appendMarketplaceDirectorySections(body, userMessage);
      }

      if (includeSponsorshipPackages) {
        hasContent |= appendSponsorshipPackagesSection(body);
      }

      if (!hasContent) {
        return "";
      }

      return finalizeRagBlock(body);
    } catch (Exception e) {
      log.warn("Support chat RAG retrieval failed: {}", e.getMessage());
      return "";
    }
  }

  private String finalizeRagBlock(StringBuilder body) {
    String header =
        "FACTUAL_SNIPPETS_FROM_DATABASE (live data; may be incomplete). "
            + "When you mention a specific listing, company, or sponsorship package below, stick "
            + "to these facts. "
            + "Do not invent prices or availability. If the user asks for something not listed, "
            + "say so and suggest they use Marketplace → Real estate, /properties, or Marketplace "
            + "categories; for exhibition packages use homepage exhibition/contact flows.\n\n";
    String out = (header + body).trim();
    if (out.length() > maxChars) {
      return out.substring(0, maxChars) + "\n…(truncated)";
    }
    return out;
  }

  /** Short plain-text hints for the rule-based fallback when no LLM is configured. */
  @Transactional(readOnly = true)
  public String buildFallbackHints(String userMessage) {
    if (!ragEnabled || !StringUtils.hasText(userMessage)) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    List<String> tokens = extractSearchTokens(userMessage);
    if (!tokens.isEmpty()) {
      try {
        List<Property> properties = findMatchingProperties(tokens);
        if (!properties.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          sb.append(
              "Here are some current listings that may match your message (use Marketplace → Real "
                  + "estate or /properties on the site for full details): ");
          int n = Math.min(4, properties.size());
          for (int i = 0; i < n; i++) {
            Property p = properties.get(i);
            if (i > 0) {
              sb.append(" ");
            }
            sb.append("\"").append(truncate(p.getTitle(), 80)).append("\"");
            if (StringUtils.hasText(p.getCity())) {
              sb.append(" (").append(p.getCity()).append(")");
            }
            sb.append(".");
          }
          parts.add(sb.toString());
        }
      } catch (Exception e) {
        // ignore
      }
    }
    if (includeSponsorshipPackages && mentionsExhibitionSponsorship(userMessage)) {
      String line = buildSponsorshipFallbackLine();
      if (StringUtils.hasText(line)) {
        parts.add(line);
      }
    }
    if (includeMarketplaceDirectory) {
      String line = buildMarketplaceDirectoryFallbackLine(userMessage);
      if (StringUtils.hasText(line)) {
        parts.add(line);
      }
    }
    return parts.isEmpty() ? "" : String.join(" ", parts);
  }

  private boolean mentionsMarketplaceDirectoryIntent(String message) {
    String lower = message.toLowerCase(Locale.ROOT);
    if (!hasDirectoryQualityIntent(lower)) {
      return false;
    }
    return hasSectorOrEntityHint(lower);
  }

  private boolean hasDirectoryQualityIntent(String lower) {
    return lower.contains("best")
        || lower.contains("top ")
        || lower.contains("list ")
        || lower.contains("lists ")
        || lower.contains("recommend")
        || lower.contains("contact")
        || lower.contains("phone")
        || lower.contains("email")
        || lower.contains("call ")
        || lower.contains("reach")
        || lower.contains("verified")
        || lower.contains("verification")
        || lower.contains("rating")
        || lower.contains("review")
        || lower.contains("reputation")
        || lower.contains("compare")
        || lower.contains("who should");
  }

  private boolean hasSectorOrEntityHint(String lower) {
    if (lower.contains("real estate compan")
        || lower.contains("real estate firm")
        || lower.contains("real estate firms")) {
      return true;
    }
    return lower.contains("real estate")
        || lower.contains("realtor")
        || lower.contains("estate agent")
        || lower.contains("bank")
        || lower.contains("lending")
        || lower.contains("insurance")
        || lower.contains("insurer")
        || lower.contains("supplier")
        || lower.contains("contractor")
        || lower.contains("developer")
        || lower.contains("consultant")
        || lower.contains("architect")
        || lower.contains("finishing")
        || lower.contains("marketplace")
        || lower.contains("propert")
        || lower.contains("listing")
        || lower.contains("compan")
        || lower.contains("organization")
        || lower.contains("firm")
        || lower.contains("agency")
        || lower.contains("agent")
        || lower.contains("entity")
        || lower.contains("on the platform")
        || lower.contains("ethio build");
  }

  /**
   * What to include in the ranked marketplace directory: organizations (optionally filtered by
   * type), properties, or both. Empty {@code organizationTypesFilter} means all organization types.
   */
  private record DirectoryTargets(
      boolean includeOrganizations,
      boolean includeProperties,
      Set<Organization.OrganizationType> organizationTypesFilter) {}

  private DirectoryTargets resolveDirectoryTargets(String message) {
    String lower = message.toLowerCase(Locale.ROOT);
    boolean propKw = propertyDirectoryKeywords(lower);
    boolean orgKw = organizationDirectoryKeywords(lower);
    boolean genericEntity = genericEntityKeywords(lower);
    boolean exclusivePropertyOnly = propKw && !orgKw && !genericEntity;
    boolean includeOrgs = !exclusivePropertyOnly;
    boolean includeProps = propKw;
    Set<Organization.OrganizationType> typeFilter = inferOrganizationTypeFilter(lower);
    return new DirectoryTargets(includeOrgs, includeProps, typeFilter);
  }

  private boolean propertyDirectoryKeywords(String lower) {
    return lower.contains("propert")
        || lower.contains("listing")
        || lower.contains("listings")
        || lower.contains("homes")
        || (lower.contains("home")
            && (lower.contains("buy") || lower.contains("rent") || lower.contains("sale")))
        || lower.contains("apartment for")
        || lower.contains("house for");
  }

  private boolean organizationDirectoryKeywords(String lower) {
    return lower.contains("bank")
        || lower.contains("insurance")
        || lower.contains("supplier")
        || lower.contains("contractor")
        || lower.contains("developer")
        || lower.contains("consultant")
        || lower.contains("architect")
        || lower.contains("finishing")
        || lower.contains("real estate")
        || lower.contains("realtor")
        || lower.contains("estate agent")
        || lower.contains("firm")
        || lower.contains("agency");
  }

  private boolean genericEntityKeywords(String lower) {
    return lower.contains("compan")
        || lower.contains("organization")
        || lower.contains("marketplace")
        || lower.contains("entity")
        || lower.contains("on the platform")
        || lower.contains("supplier"); // plural "suppliers" contains supplier
  }

  /** Empty set = include all {@link Organization.OrganizationType} values. */
  private Set<Organization.OrganizationType> inferOrganizationTypeFilter(String lower) {
    Set<Organization.OrganizationType> s = new LinkedHashSet<>();
    if (lower.contains("bank") || lower.contains("lending")) {
      s.add(Organization.OrganizationType.BANK);
    }
    if (lower.contains("insurance") || lower.contains("insurer")) {
      s.add(Organization.OrganizationType.INSURANCE);
    }
    if (lower.contains("supplier")) {
      s.add(Organization.OrganizationType.SUPPLIER);
    }
    if (lower.contains("contractor") && !lower.contains("finishing")) {
      s.add(Organization.OrganizationType.CONTRACTOR);
    }
    if (lower.contains("developer")) {
      s.add(Organization.OrganizationType.DEVELOPER);
    }
    if (lower.contains("consultant") || lower.contains("architect")) {
      s.add(Organization.OrganizationType.CONSULTANT_ARCHITECT);
    }
    if (lower.contains("finishing")) {
      s.add(Organization.OrganizationType.FINISHING_CONTRACTOR);
    }
    if (lower.contains("real estate")
        || lower.contains("realtor")
        || lower.contains("estate agent")) {
      s.add(Organization.OrganizationType.REAL_ESTATE_COMPANY);
    }
    return s;
  }

  private boolean mentionsExhibitionSponsorship(String message) {
    String lower = message.toLowerCase(Locale.ROOT);
    String[] keys = {
      "sponsor",
      "sponsorship",
      "exhibition",
      "exhibitor",
      "platinum",
      "gold",
      "silver",
      "exclusive",
      "special",
      "package",
      "tier",
      "booth",
      "expo"
    };
    for (String k : keys) {
      if (lower.contains(k)) {
        return true;
      }
    }
    return false;
  }

  private String buildSponsorshipFallbackLine() {
    try {
      List<SponsorshipResponse> list = sponsorshipService.getActiveSponsorships();
      if (list == null || list.isEmpty()) {
        return "";
      }
      List<SponsorshipResponse> sorted = new ArrayList<>(list);
      sorted.sort(Comparator.comparingInt(r -> r.getType().tierRank()));
      StringBuilder sb = new StringBuilder();
      sb.append("Active exhibition sponsorship packages (see homepage for details): ");
      for (int i = 0; i < sorted.size(); i++) {
        SponsorshipResponse r = sorted.get(i);
        if (i > 0) {
          sb.append("; ");
        }
        sb.append(r.getType()).append(" — ").append(truncate(r.getName(), 48));
        if (r.getBasePrice() != null) {
          sb.append(" (from ")
              .append(r.getBasePrice().stripTrailingZeros().toPlainString())
              .append(" ETB)");
        }
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }

  private void appendPropertiesSection(StringBuilder sb, List<Property> properties) {
    sb.append("PROPERTIES:\n");
    for (Property p : properties) {
      sb.append("- ");
      sb.append(truncate(p.getTitle(), 120));
      sb.append(" | id=").append(p.getId());
      if (StringUtils.hasText(p.getCity())) {
        sb.append(" | city=").append(p.getCity());
      }
      if (StringUtils.hasText(p.getAddress())) {
        sb.append(" | address=").append(truncate(p.getAddress(), 80));
      }
      sb.append(" | type=").append(p.getType());
      if (p.getPriceETB() != null) {
        sb.append(" | priceETB=").append(p.getPriceETB().stripTrailingZeros().toPlainString());
      }
      if (p.getCategory() != null) {
        sb.append(" | category=").append(p.getCategory());
      }
      sb.append("\n");
    }
    sb.append("\n");
  }

  private void appendOrganizationsSection(StringBuilder sb, List<Organization> organizations) {
    sb.append("ORGANIZATIONS:\n");
    for (Organization o : organizations) {
      sb.append("- ");
      sb.append(truncate(o.getName(), 120));
      sb.append(" | id=").append(o.getId());
      sb.append(" | type=").append(o.getType());
      if (StringUtils.hasText(o.getCity())) {
        sb.append(" | city=").append(o.getCity());
      }
      sb.append("\n");
    }
    sb.append("\n");
  }

  private boolean appendMarketplaceDirectorySections(StringBuilder body, String userMessage) {
    if (!mentionsMarketplaceDirectoryIntent(userMessage)) {
      return false;
    }
    DirectoryTargets t = resolveDirectoryTargets(userMessage);
    boolean any = false;
    if (t.includeOrganizations()) {
      any |= appendOrganizationsRankedSection(body, t.organizationTypesFilter());
    }
    if (t.includeProperties()) {
      any |= appendPropertiesRankedSection(body);
    }
    return any;
  }

  /**
   * @return true if at least one organization was written
   */
  private boolean appendOrganizationsRankedSection(
      StringBuilder sb, Set<Organization.OrganizationType> typeFilter) {
    OrgDirectoryData data = loadRankedOrganizationsDirectoryData(typeFilter);
    if (data.companies().isEmpty()) {
      return false;
    }
    List<Organization> ranked = data.companies();
    Map<UUID, RatingAgg> stats = data.reviewStats();
    sb.append(
        "ORGANIZATIONS_RANKED (APPROVED organizations; sorted by verification FULL>HALF>NONE, then "
            + "average organization review score, then review count; profileUrl = public page):\n");
    for (Organization o : ranked) {
      sb.append("- orgType=").append(o.getType());
      sb.append(" | name=").append(truncate(o.getName(), 120));
      sb.append(" | id=").append(o.getId());
      sb.append(" | verificationLevel=").append(o.getVerificationLevel());
      sb.append(" | verified=")
          .append(o.getVerificationLevel() == Organization.VerificationLevel.FULL);
      RatingAgg agg = stats.getOrDefault(o.getId(), new RatingAgg(0.0, 0));
      if (agg.count() > 0) {
        sb.append(" | avgOrgReview=").append(round1(agg.avg())).append("/5");
        sb.append(" | orgReviewCount=").append(agg.count());
      } else {
        sb.append(" | avgOrgReview=none | orgReviewCount=0");
      }
      if (StringUtils.hasText(o.getCity())) {
        sb.append(" | city=").append(truncate(o.getCity(), 64));
      }
      if (StringUtils.hasText(o.getAddress())) {
        sb.append(" | address=").append(truncate(o.getAddress(), 80));
      }
      OrganizationContact c = o.getContact();
      if (c != null) {
        if (StringUtils.hasText(c.getEmail())) {
          sb.append(" | email=").append(truncate(c.getEmail(), 80));
        }
        if (StringUtils.hasText(c.getWebsite())) {
          sb.append(" | website=").append(truncate(c.getWebsite(), 96));
        }
      }
      String phones = formatOrganizationPhones(o);
      if (StringUtils.hasText(phones)) {
        sb.append(" | phones=").append(truncate(phones, 120));
      }
      sb.append(" | profileUrl=").append(organizationProfileUrl(o.getId()));
      sb.append("\n");
    }
    sb.append("\n");
    return true;
  }

  /**
   * @return true if at least one property was written
   */
  private boolean appendPropertiesRankedSection(StringBuilder sb) {
    PropertyDirectoryData data = loadRankedPropertiesDirectoryData();
    if (data.properties().isEmpty()) {
      return false;
    }
    Map<UUID, RatingAgg> propStats = data.propertyReviewStats();
    Map<UUID, Organization> listingOrgs = data.listingOrganizationsById();
    sb.append(
        "PROPERTIES_RANKED (AVAILABLE listings; listing company must be APPROVED when present; "
            + "REJECTED listings omitted; sorted by listing verification VERIFIED>PENDING, then "
            + "average property review score, then review count; listingContact* from listing org):\n");
    for (Property p : data.properties()) {
      sb.append("- title=").append(truncate(p.getTitle(), 120));
      sb.append(" | id=").append(p.getId());
      sb.append(" | listingVerification=").append(p.getVerificationStatus());
      RatingAgg agg = propStats.getOrDefault(p.getId(), new RatingAgg(0.0, 0));
      if (agg.count() > 0) {
        sb.append(" | avgPropertyReview=").append(round1(agg.avg())).append("/5");
        sb.append(" | propertyReviewCount=").append(agg.count());
      } else {
        sb.append(" | avgPropertyReview=none | propertyReviewCount=0");
      }
      if (StringUtils.hasText(p.getCity())) {
        sb.append(" | city=").append(truncate(p.getCity(), 64));
      }
      if (p.getPriceETB() != null) {
        sb.append(" | priceETB=").append(p.getPriceETB().stripTrailingZeros().toPlainString());
      }
      sb.append(" | category=").append(p.getCategory());
      UUID companyId = p.getRealEstateCompanyId();
      if (companyId != null) {
        Organization lo = listingOrgs.get(companyId);
        if (lo != null) {
          sb.append(" | listingCompany=").append(truncate(lo.getName(), 80));
          OrganizationContact c = lo.getContact();
          if (c != null) {
            if (StringUtils.hasText(c.getEmail())) {
              sb.append(" | listingContactEmail=").append(truncate(c.getEmail(), 72));
            }
            if (StringUtils.hasText(c.getWebsite())) {
              sb.append(" | listingContactWebsite=").append(truncate(c.getWebsite(), 80));
            }
          }
          String phones = formatOrganizationPhones(lo);
          if (StringUtils.hasText(phones)) {
            sb.append(" | listingContactPhones=").append(truncate(phones, 100));
          }
        }
      }
      sb.append(" | propertyUrl=").append(propertyProfileUrl(p.getId()));
      sb.append("\n");
    }
    sb.append("\n");
    return true;
  }

  private String buildMarketplaceDirectoryFallbackLine(String userMessage) {
    if (!mentionsMarketplaceDirectoryIntent(userMessage)) {
      return "";
    }
    DirectoryTargets t = resolveDirectoryTargets(userMessage);
    List<String> bits = new ArrayList<>();
    if (t.includeOrganizations()) {
      OrgDirectoryData orgData = loadRankedOrganizationsDirectoryData(t.organizationTypesFilter());
      List<Organization> ranked = orgData.companies();
      if (!ranked.isEmpty()) {
        int n = Math.min(4, ranked.size());
        StringBuilder sb = new StringBuilder();
        sb.append("Organizations (by verification & reviews): ");
        Map<UUID, RatingAgg> stats = orgData.reviewStats();
        for (int i = 0; i < n; i++) {
          Organization o = ranked.get(i);
          if (i > 0) {
            sb.append("; ");
          }
          sb.append(o.getType()).append(" ").append(truncate(o.getName(), 40));
          RatingAgg agg = stats.getOrDefault(o.getId(), new RatingAgg(0.0, 0));
          if (agg.count() > 0) {
            sb.append(" (").append(round1(agg.avg())).append("/5)");
          }
        }
        bits.add(sb.toString());
      }
    }
    if (t.includeProperties()) {
      PropertyDirectoryData propData = loadRankedPropertiesDirectoryData();
      if (!propData.properties().isEmpty()) {
        int n = Math.min(3, propData.properties().size());
        StringBuilder sb = new StringBuilder();
        sb.append("Sample listings (by verification & reviews): ");
        for (int i = 0; i < n; i++) {
          Property p = propData.properties().get(i);
          if (i > 0) {
            sb.append("; ");
          }
          sb.append("\"").append(truncate(p.getTitle(), 36)).append("\"");
          if (StringUtils.hasText(p.getCity())) {
            sb.append(" (").append(p.getCity()).append(")");
          }
        }
        bits.add(sb.toString());
      }
    }
    return bits.isEmpty() ? "" : String.join(" ", bits);
  }

  private record RatingAgg(double avg, long count) {}

  private record OrgDirectoryData(List<Organization> companies, Map<UUID, RatingAgg> reviewStats) {}

  private record PropertyDirectoryData(
      List<Property> properties,
      Map<UUID, RatingAgg> propertyReviewStats,
      Map<UUID, Organization> listingOrganizationsById) {}

  private OrgDirectoryData loadRankedOrganizationsDirectoryData(
      Set<Organization.OrganizationType> typeFilter) {
    List<Organization.OrganizationType> types =
        typeFilter == null || typeFilter.isEmpty()
            ? List.of(Organization.OrganizationType.values())
            : new ArrayList<>(typeFilter);
    List<Organization> orgs =
        organizationRepository.findByStatusAndTypeInWithContact(
            Organization.OrganizationStatus.APPROVED, types);
    if (orgs.isEmpty()) {
      return new OrgDirectoryData(List.of(), Map.of());
    }
    List<UUID> ids = orgs.stream().map(Organization::getId).toList();
    Map<UUID, RatingAgg> stats = reviewAggregatesForOrganizations(ids);
    List<Organization> ranked =
        orgs.stream()
            .sorted(
                Comparator.comparingInt(
                        (Organization o) -> verificationOrdinal(o.getVerificationLevel()))
                    .reversed()
                    .thenComparing(
                        o -> stats.getOrDefault(o.getId(), new RatingAgg(0.0, 0)).avg(),
                        Comparator.reverseOrder())
                    .thenComparing(
                        o -> stats.getOrDefault(o.getId(), new RatingAgg(0.0, 0)).count(),
                        Comparator.reverseOrder())
                    .thenComparing(
                        o -> o.getName() != null ? o.getName() : "", String.CASE_INSENSITIVE_ORDER))
            .limit(maxDirectoryOrganizations)
            .toList();
    return new OrgDirectoryData(ranked, stats);
  }

  private PropertyDirectoryData loadRankedPropertiesDirectoryData() {
    List<Property> pool =
        propertyRepository.findByStatusOrderByCreatedAtDesc(
            Property.PropertyStatus.AVAILABLE,
            PageRequest.of(
                0, directoryPropertyCandidatePool, Sort.by(Sort.Direction.DESC, "createdAt")));
    Set<UUID> companyIds =
        pool.stream()
            .map(Property::getRealEstateCompanyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, Organization> orgById = new HashMap<>();
    if (!companyIds.isEmpty()) {
      orgById.putAll(
          organizationRepository.findAllByIdInWithContact(companyIds).stream()
              .filter(OrganizationPublicVisibility::isPubliclyListed)
              .collect(Collectors.toMap(Organization::getId, o -> o, (a, b) -> a)));
    }
    List<Property> visible =
        pool.stream()
            .filter(
                p -> {
                  if (p.getVerificationStatus() == Property.VerificationStatus.REJECTED) {
                    return false;
                  }
                  if (p.getRealEstateCompanyId() == null) {
                    return true;
                  }
                  return orgById.containsKey(p.getRealEstateCompanyId());
                })
            .toList();
    List<UUID> propIds = visible.stream().map(Property::getId).toList();
    Map<UUID, RatingAgg> propStats = reviewAggregatesForProperties(propIds);
    List<Property> ranked =
        visible.stream()
            .sorted(
                Comparator.comparingInt(
                        (Property p) -> propertyVerificationOrdinal(p.getVerificationStatus()))
                    .reversed()
                    .thenComparing(
                        p -> propStats.getOrDefault(p.getId(), new RatingAgg(0.0, 0)).avg(),
                        Comparator.reverseOrder())
                    .thenComparing(
                        p -> propStats.getOrDefault(p.getId(), new RatingAgg(0.0, 0)).count(),
                        Comparator.reverseOrder())
                    .thenComparing(
                        Property::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(maxDirectoryProperties)
            .toList();
    return new PropertyDirectoryData(ranked, propStats, orgById);
  }

  private Map<UUID, RatingAgg> reviewAggregatesForOrganizations(List<UUID> organizationIds) {
    Map<UUID, RatingAgg> out = new HashMap<>();
    if (organizationIds == null || organizationIds.isEmpty()) {
      return out;
    }
    List<Object[]> rows = reviewRepository.aggregateReviewStatsByOrganizationIds(organizationIds);
    for (Object[] row : rows) {
      UUID id = (UUID) row[0];
      double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
      long cnt = row[2] != null ? ((Number) row[2]).longValue() : 0L;
      out.put(id, new RatingAgg(avg, cnt));
    }
    return out;
  }

  private Map<UUID, RatingAgg> reviewAggregatesForProperties(List<UUID> propertyIds) {
    Map<UUID, RatingAgg> out = new HashMap<>();
    if (propertyIds == null || propertyIds.isEmpty()) {
      return out;
    }
    List<Object[]> rows = reviewRepository.aggregateReviewStatsByPropertyIds(propertyIds);
    for (Object[] row : rows) {
      UUID id = (UUID) row[0];
      double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
      long cnt = row[2] != null ? ((Number) row[2]).longValue() : 0L;
      out.put(id, new RatingAgg(avg, cnt));
    }
    return out;
  }

  private static int propertyVerificationOrdinal(Property.VerificationStatus v) {
    if (v == null) {
      return 0;
    }
    return switch (v) {
      case VERIFIED -> 2;
      case PENDING -> 1;
      case REJECTED -> 0;
    };
  }

  private static int verificationOrdinal(Organization.VerificationLevel v) {
    if (v == null) {
      return 0;
    }
    return switch (v) {
      case FULL -> 2;
      case HALF -> 1;
      case NONE -> 0;
    };
  }

  private static String round1(double v) {
    return String.format(Locale.ROOT, "%.1f", v);
  }

  private String organizationProfileUrl(UUID id) {
    String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
    return base + "/organizations/" + id;
  }

  private String propertyProfileUrl(UUID id) {
    String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
    return base + "/properties/" + id;
  }

  private static String formatOrganizationPhones(Organization org) {
    OrganizationContact c = org.getContact();
    if (c == null || c.getPhones() == null || c.getPhones().isEmpty()) {
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

  /**
   * @return true if at least one active package was written
   */
  private boolean appendSponsorshipPackagesSection(StringBuilder sb) {
    List<SponsorshipResponse> list = sponsorshipService.getActiveSponsorships();
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<SponsorshipResponse> sorted = new ArrayList<>(list);
    sorted.sort(Comparator.comparingInt(r -> r.getType().tierRank()));
    sb.append(
        "SPONSORSHIP_PACKAGES (exhibition; ACTIVE packages — same data as public pricing):\n");
    for (SponsorshipResponse r : sorted) {
      sb.append("- type=").append(r.getType());
      sb.append(" | name=").append(truncate(r.getName(), 96));
      sb.append(" | packageId=").append(r.getId());
      if (r.getBasePrice() != null) {
        sb.append(" | basePriceETB=").append(r.getBasePrice().stripTrailingZeros().toPlainString());
      }
      if (StringUtils.hasText(r.getDescription())) {
        sb.append(" | description=").append(truncate(r.getDescription(), 240));
      }
      if (StringUtils.hasText(r.getFeatures())) {
        sb.append(" | features=").append(truncate(r.getFeatures(), 240));
      }
      sb.append("\n");
    }
    sb.append("\n");
    return true;
  }

  private List<Property> findMatchingProperties(List<String> tokens) {
    Specification<Property> spec =
        Specification.where(
            (root, query, cb) -> cb.equal(root.get("status"), Property.PropertyStatus.AVAILABLE));

    Specification<Property> tokenOr =
        (root, query, cb) -> {
          List<Predicate> tokenPredicates = new ArrayList<>();
          for (String token : tokens) {
            String pattern = "%" + token.toLowerCase(Locale.ROOT) + "%";
            Predicate title = cb.like(cb.lower(root.get("title")), pattern);
            Predicate desc = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
            Predicate addr = cb.like(cb.lower(cb.coalesce(root.get("address"), "")), pattern);
            Predicate city = cb.like(cb.lower(cb.coalesce(root.get("city"), "")), pattern);
            tokenPredicates.add(cb.or(title, desc, addr, city));
          }
          return cb.or(tokenPredicates.toArray(Predicate[]::new));
        };

    spec = spec.and(tokenOr);
    List<Property> found = propertyRepository.findAll(spec);

    Set<UUID> orgIds =
        found.stream()
            .map(Property::getRealEstateCompanyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (orgIds.isEmpty()) {
      return List.of();
    }
    List<Organization> orgs = organizationRepository.findAllById(orgIds);
    Set<UUID> visibleOrgIds =
        orgs.stream()
            .filter(OrganizationPublicVisibility::isPubliclyListed)
            .map(Organization::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return found.stream()
        .filter(
            p ->
                p.getRealEstateCompanyId() != null
                    && visibleOrgIds.contains(p.getRealEstateCompanyId()))
        .sorted(
            Comparator.comparing(
                Property::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(maxProperties)
        .collect(Collectors.toList());
  }

  private List<Organization> findMatchingOrganizations(String raw) {
    String q = raw.length() > 120 ? raw.substring(0, 120) : raw;
    List<Organization> hits = organizationRepository.searchOrganizations(q);
    return hits.stream()
        .filter(OrganizationPublicVisibility::isPubliclyListed)
        .limit(maxOrganizations)
        .collect(Collectors.toList());
  }

  static List<String> extractSearchTokens(String message) {
    if (!StringUtils.hasText(message)) {
      return List.of();
    }
    String normalized =
        Normalizer.normalize(message.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    String[] parts = normalized.split("[^\\p{L}\\p{N}]+");
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      if (p.length() >= 2 && p.length() <= 48) {
        out.add(p);
      }
    }
    if (out.size() > 8) {
      out = out.subList(0, 8);
    }
    return out;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    String t = s.replace('\n', ' ').trim();
    if (t.length() <= max) {
      return t;
    }
    return t.substring(0, max) + "…";
  }
}
