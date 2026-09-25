package com.housingplatform.payment.chapa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.purchase.service.PurchaseDepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChapaWebhookControllerTest {

  private static final String SECRET = "whsec_test";

  private PurchaseDepositService depositService;
  private com.housingplatform.purchase.service.PurchaseBalanceService balanceService;
  private ChapaWebhookController controller;

  @BeforeEach
  void setUp() {
    ChapaProperties properties = new ChapaProperties();
    properties.setWebhookSecret(SECRET);
    depositService = mock(PurchaseDepositService.class);
    balanceService = mock(com.housingplatform.purchase.service.PurchaseBalanceService.class);
    controller =
        new ChapaWebhookController(properties, depositService, balanceService, new ObjectMapper());
  }

  @Test
  void rejectsUnsignedAndMisSignedRequests() {
    String body = "{\"tx_ref\":\"PPO-1-DEP1-ABC\"}";
    assertThat(controller.webhook(body, null, null).getStatusCode().value()).isEqualTo(401);
    String wrong = ChapaSignature.hmacSha256Hex("other-secret", body);
    assertThat(controller.webhook(body, null, wrong).getStatusCode().value()).isEqualTo(401);
    verify(depositService, never()).handleProviderNotification(any());
  }

  @Test
  void acceptsAChapaGlobalWebhookSignedOverTheRawBody() {
    String body =
        "{\"webhook_type\":\"payment\",\"status\":\"success\","
            + "\"merchant_reference\":\"PPO-1-DEP1-ABC\",\"chapa_reference\":\"CH123\"}";
    String sig = ChapaSignature.hmacSha256Hex(SECRET, body);
    assertThat(controller.webhook(body, null, sig).getStatusCode().value()).isEqualTo(200);
    verify(depositService).handleProviderNotification("PPO-1-DEP1-ABC");
  }

  @Test
  void balancePaymentsAreRoutedToTheBalanceService() {
    String body = "{\"merchant_reference\":\"3F9A1C2B-B1-7K2Q\"}";
    org.mockito.Mockito.when(balanceService.ownsTxRef("3F9A1C2B-B1-7K2Q")).thenReturn(true);
    String sig = ChapaSignature.hmacSha256Hex(SECRET, body);
    assertThat(controller.webhook(body, null, sig).getStatusCode().value()).isEqualTo(200);
    verify(balanceService).handleProviderNotification("3F9A1C2B-B1-7K2Q");
    verify(depositService, never()).handleProviderNotification(any());
  }
}
