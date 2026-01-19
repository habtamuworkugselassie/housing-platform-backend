package com.housingplatform.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        
        // Check if URL contains embedded credentials (format: jdbc:postgresql://user:pass@host/db)
        if (StringUtils.hasText(datasourceUrl) && datasourceUrl.contains("@") && 
            !StringUtils.hasText(datasourceUsername)) {
            try {
                ParsedDatabaseUrl parsed = parseDatabaseUrl(datasourceUrl);
                properties.setUrl(parsed.getUrl());
                properties.setUsername(parsed.getUsername());
                properties.setPassword(parsed.getPassword());
            } catch (Exception e) {
                // If parsing fails, use original URL
                properties.setUrl(datasourceUrl);
                if (StringUtils.hasText(datasourceUsername)) {
                    properties.setUsername(datasourceUsername);
                }
                if (StringUtils.hasText(datasourcePassword)) {
                    properties.setPassword(datasourcePassword);
                }
            }
        } else {
            properties.setUrl(datasourceUrl);
            if (StringUtils.hasText(datasourceUsername)) {
                properties.setUsername(datasourceUsername);
            }
            if (StringUtils.hasText(datasourcePassword)) {
                properties.setPassword(datasourcePassword);
            }
        }
        
        return properties;
    }

    private ParsedDatabaseUrl parseDatabaseUrl(String url) throws URISyntaxException {
        // Handle JDBC URL format: jdbc:postgresql://user:pass@host:port/database
        if (url.startsWith("jdbc:")) {
            String jdbcPrefix = "jdbc:";
            String dbUrl = url.substring(jdbcPrefix.length());
            
            // Parse postgresql://user:pass@host:port/database
            URI uri = new URI(dbUrl);
            
            String username = uri.getUserInfo() != null ? uri.getUserInfo().split(":")[0] : null;
            String password = uri.getUserInfo() != null && uri.getUserInfo().contains(":") 
                ? uri.getUserInfo().substring(uri.getUserInfo().indexOf(":") + 1) : null;
            
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : 5432; // Default PostgreSQL port
            String database = uri.getPath() != null ? uri.getPath().replaceFirst("/", "") : null;
            
            String cleanUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            
            return new ParsedDatabaseUrl(cleanUrl, username, password);
        }
        
        throw new IllegalArgumentException("Unsupported database URL format: " + url);
    }

    private static class ParsedDatabaseUrl {
        private final String url;
        private final String username;
        private final String password;

        public ParsedDatabaseUrl(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        public String getUrl() {
            return url;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
