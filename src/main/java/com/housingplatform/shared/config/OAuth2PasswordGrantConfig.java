package com.housingplatform.shared.config;

import com.housingplatform.shared.security.grant_types.password.OAuth2PasswordAuthenticationConverter;
import com.housingplatform.shared.security.grant_types.password.OAuth2PasswordAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for OAuth2 Password Grant Type
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2PasswordGrantConfig {
    
    private final OAuth2PasswordAuthenticationProvider passwordAuthenticationProvider;
    
    @Bean
    public AuthenticationConverter passwordAuthenticationConverter() {
        return new OAuth2PasswordAuthenticationConverter();
    }
    
    @Bean
    public RequestMatcher passwordGrantRequestMatcher() {
        return request -> {
            String grantType = request.getParameter("grant_type");
            return "password".equals(grantType);
        };
    }
}
