package com.housingplatform.shared.security.grant_types.password;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Set;

/**
 * OAuth2 Password Grant Authentication Token
 */
public class OAuth2PasswordAuthenticationToken extends AbstractAuthenticationToken {
    
    public static final String CREDENTIAL_TYPE = "credential_type";
    public static final String VERIFICATION_CHANNEL = "verification_channel";
    public static final String VERIFICATION_CODE = "verification_code";
    
    private final String username;
    private final String password;
    private final Authentication clientPrincipal;
    private final Set<String> scopes;
    private final Map<String, Object> additionalParameters;
    
    public OAuth2PasswordAuthenticationToken(String username, String password, 
                                            Authentication clientPrincipal, 
                                            Set<String> scopes,
                                            Map<String, Object> additionalParameters) {
        super(null);
        this.username = username;
        this.password = password;
        this.clientPrincipal = clientPrincipal;
        this.scopes = scopes;
        this.additionalParameters = additionalParameters;
        setAuthenticated(false);
    }
    
    @Override
    public Object getCredentials() {
        return this.password;
    }
    
    @Override
    public Object getPrincipal() {
        return this.username;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public Authentication getClientPrincipal() {
        return clientPrincipal;
    }
    
    public Set<String> getScopes() {
        return scopes;
    }
    
    public Map<String, Object> getAdditionalParameters() {
        return additionalParameters;
    }
}
