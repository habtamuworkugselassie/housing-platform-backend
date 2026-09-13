package com.housingplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class
      // Redis is conditionally enabled via RedisConfig only when rate limiting is
      // enabled
    })
@EnableJpaAuditing
@EnableAsync
@EnableCaching
// Expo reminder mail runs on a daily cron; see ExhibitionReminderScheduler.
@EnableScheduling
public class HousingPlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(HousingPlatformApplication.class, args);
  }
}
