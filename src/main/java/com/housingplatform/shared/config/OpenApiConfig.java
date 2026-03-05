package com.housingplatform.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI housingPlatformOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Housing Platform API")
                .description(
                    "Integrated Real Estate & Housing Ecosystem Platform - REST API Documentation")
                .version("1.0.0")
                .contact(
                    new Contact()
                        .name("Housing Platform Team")
                        .email("support@housingplatform.com"))
                .license(new License().name("Proprietary").url("https://housingplatform.com")))
        .servers(
            List.of(
                new Server().url("/").description("Current Server (Droplet/Local)"),
                new Server().url("http://localhost:8080").description("Local Development Server"),
                new Server().url("http://209.38.204.219:8080").description("Production Server")));
  }
}
