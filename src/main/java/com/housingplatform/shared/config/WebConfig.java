package com.housingplatform.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Files are now stored directly in the database, so no static file serving needed
        // This prevents Spring from trying to serve static resources and causing errors
        // If you need to serve static files in the future, add handlers here
    }
}
