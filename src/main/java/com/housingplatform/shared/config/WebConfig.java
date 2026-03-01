package com.housingplatform.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // Serve sample real estate logos and images from classpath:realestates/ (used by V22 migration)
    registry
        .addResourceHandler("/realestates/**")
        .addResourceLocations("classpath:realestates/");
  }
}
