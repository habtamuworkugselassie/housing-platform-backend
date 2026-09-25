package com.housingplatform.property.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.domain.PropertyDocument;
import com.housingplatform.property.domain.PropertyDocument.DocumentStatus;
import com.housingplatform.property.domain.PropertyDocument.DocumentType;
import com.housingplatform.property.repository.PropertyDocumentRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.service.PropertyDocumentService.Upload;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyDocumentServiceImplTest {

  @Mock private PropertyDocumentRepository documentRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private RealEstateAgentRepository agentRepository;
  @Mock private MediaStorageService storage;
  @InjectMocks private PropertyDocumentServiceImpl service;

  private MockedStatic<UserContext> ctx;
  private final UUID userId = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();
  private Property property;
  private RealEstateAgent agent;

  @BeforeEach
  void setUp() {
    ctx = mockStatic(UserContext.class);
    ctx.when(UserContext::getCurrentUserIdOrNull).thenReturn(userId);
    ctx.when(UserContext::isAdmin).thenReturn(false);

    Organization org = new Organization();
    org.setId(companyId);
    agent = new RealEstateAgent();
    agent.setId(UUID.randomUUID());
    agent.setOrganization(org);
    when(agentRepository.findByUserId(userId)).thenReturn(Optional.of(agent));

    property = Property.builder().title("Villa").realEstateCompanyId(companyId).build();
    property.setId(UUID.randomUUID());
    property.setAgentId(agent.getId());
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
    when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(storage.save(any(), anyString()))
        .thenReturn("/api/v1/uploads/private/property-documents/x.pdf");
  }

  @AfterEach
  void tearDown() {
    ctx.close();
  }

  private static MockMultipartFile pdf() {
    return new MockMultipartFile("file", "deed.pdf", "application/pdf", "%PDF-1.4 deed".getBytes());
  }

  private static Upload deed() {
    return new Upload(DocumentType.TITLE_DEED, "AA/1234", "Addis Ababa Land Admin", null, null);
  }

  @Test
  void theListingAgentUploadsAPendingPrivateDocumentWithItsFingerprint() {
    var r = service.upload(property.getId(), pdf(), deed());
    assertThat(r.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(r.getSha256()).hasSize(64);
    assertThat(r.getDocumentType()).isEqualTo(DocumentType.TITLE_DEED);
    verify(storage)
        .save(any(), org.mockito.ArgumentMatchers.startsWith("private/property-documents/"));
  }

  @Test
  void anotherRealtorCannotManageTheListing() {
    property.setAgentId(UUID.randomUUID());
    assertThatThrownBy(() -> service.upload(property.getId(), pdf(), deed()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("own listings");
    verify(storage, never()).save(any(), anyString());
  }

  @Test
  void aSuperAgentOfTheCompanyCanManageIt() {
    property.setAgentId(UUID.randomUUID());
    agent.setIsSuperAgent(true);
    assertThat(service.upload(property.getId(), pdf(), deed()).getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void aRealtorWithoutAnAgentRecordManagesNothing() {
    when(agentRepository.findByUserId(userId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.listForManager(property.getId()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void onlyPdfJpgAndPngAreAccepted() {
    var exe = new MockMultipartFile("file", "deed.exe", "application/x-msdownload", new byte[] {1});
    assertThatThrownBy(() -> service.upload(property.getId(), exe, deed()))
        .hasMessageContaining("PDF, JPG or PNG");
    assertThat(PropertyDocumentServiceImpl.normaliseType("application/octet-stream", "Deed.PDF"))
        .isEqualTo("application/pdf");
  }

  @Test
  void aVerifiedDocumentCanOnlyBeRemovedByAnAdmin() {
    PropertyDocument doc =
        PropertyDocument.builder().property(property).status(DocumentStatus.VERIFIED).build();
    doc.setId(UUID.randomUUID());
    when(documentRepository.findByIdAndPropertyId(doc.getId(), property.getId()))
        .thenReturn(Optional.of(doc));
    assertThatThrownBy(() -> service.delete(property.getId(), doc.getId()))
        .hasMessageContaining("verified");
    ctx.when(UserContext::isAdmin).thenReturn(true);
    service.delete(property.getId(), doc.getId());
    verify(documentRepository).delete(doc);
  }

  @Test
  void rejectingNeedsAReasonAndBuyersOnlyGetVerifiedFiles() {
    PropertyDocument doc =
        PropertyDocument.builder().property(property).status(DocumentStatus.PENDING).build();
    doc.setId(UUID.randomUUID());
    when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
    when(documentRepository.findByIdAndPropertyId(doc.getId(), property.getId()))
        .thenReturn(Optional.of(doc));
    assertThatThrownBy(() -> service.reject(UUID.randomUUID(), doc.getId(), " "))
        .hasMessageContaining("why");
    assertThatThrownBy(() -> service.verifiedFile(property.getId(), doc.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    var verified = service.verify(UUID.randomUUID(), doc.getId(), null);
    assertThat(verified.getStatus()).isEqualTo(DocumentStatus.VERIFIED);
    assertThat(doc.getReviewedAt()).isNotNull();
  }
}
