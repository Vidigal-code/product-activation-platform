package com.vidigal.activation.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class ConsumerIdentity {

    public static final String LOCAL_CONSUMER_ID = "local-demo";
    public static final int MAX_CONSUMER_ID_LENGTH = 255;
    private final SecurityProperties properties;

    public ConsumerIdentity(SecurityProperties properties) {
        this.properties = properties;
    }

    public String currentId() {
        if (SecurityProperties.LOCAL_MODE.equals(properties.mode())) {
            return LOCAL_CONSUMER_ID;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            String subject = token.getToken().getSubject();
            if (subject != null && !subject.isBlank() && subject.length() <= MAX_CONSUMER_ID_LENGTH) {
                return subject;
            }
        }
        throw new InsufficientAuthenticationException("Identidade do consumidor indisponível");
    }
}
