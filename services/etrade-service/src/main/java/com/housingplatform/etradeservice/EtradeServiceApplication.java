package com.housingplatform.etradeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Standalone eTrade lookup service. Wraps the Ministry of Trade portal's registration API (the one
 * behind https://etrade.gov.et/business-license-checker) behind a small, cached, API-key protected
 * HTTP API that the platform's other services call.
 */
@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class EtradeServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(EtradeServiceApplication.class, args);
  }
}
