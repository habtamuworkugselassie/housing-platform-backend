package com.housingplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
    exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class
      // Redis is conditionally enabled via RedisConfig only when rate limiting is enabled
    })
@EnableJpaAuditing
@EnableAsync
public class HousingPlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(HousingPlatformApplication.class, args);
  }
}
