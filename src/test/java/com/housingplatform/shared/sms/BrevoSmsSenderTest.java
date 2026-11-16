package com.housingplatform.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrevoSmsSenderTest {

  private static final String BASE = "https://api.brevo.test/v3";

  @Test
  void postsATransactionalSmsWithTheApiKey() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    BrevoSmsSender sms = new BrevoSmsSender("key-123", "EthioBuild", BASE, builder);

    server
        .expect(requestTo(BASE + "/transactionalSMS/send"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", "key-123"))
        .andExpect(
            content()
                .json(
                    "{\"sender\":\"EthioBuild\",\"recipient\":\"251911223344\","
                        + "\"content\":\"hi\",\"type\":\"transactional\"}"))
        .andRespond(
            withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"messageId\":1}"));

    assertThat(sms.send("+251911223344", "hi")).isTrue();
    server.verify();
  }

  @Test
  void reportsFailureInsteadOfThrowing() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    BrevoSmsSender sms = new BrevoSmsSender("key-123", "EthioBuild", BASE, builder);
    server
        .expect(requestTo(BASE + "/transactionalSMS/send"))
        .andRespond(withBadRequest().body("{\"code\":\"not_enough_credits\"}"));

    assertThat(sms.send("+251911223344", "hi")).isFalse();
  }

  @Test
  void isOffWithoutKeyOrSender() {
    assertThat(new BrevoSmsSender("", "EthioBuild", BASE, RestClient.builder()).configured())
        .isFalse();
    assertThat(new BrevoSmsSender("key", " ", BASE, RestClient.builder()).configured()).isFalse();
    assertThat(new BrevoSmsSender("key", "", BASE, RestClient.builder()).send("+2519", "x"))
        .isFalse();
  }

  @Test
  void normalisesRecipientToDigitsWithCountryCode() {
    assertThat(BrevoSmsSender.toBrevoRecipient("+251 911-223-344")).isEqualTo("251911223344");
    assertThat(BrevoSmsSender.toBrevoRecipient("00251911223344")).isEqualTo("251911223344");
    assertThat(BrevoSmsSender.toBrevoRecipient("123")).isNull();
    assertThat(BrevoSmsSender.toBrevoRecipient(null)).isNull();
  }
}
