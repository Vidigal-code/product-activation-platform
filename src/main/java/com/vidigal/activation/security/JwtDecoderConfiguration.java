package com.vidigal.activation.security;

import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "activation.security", name = "mode", havingValue = SecurityProperties.JWT_MODE,
            matchIfMissing = true)
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
                    new JwtAudienceValidator(properties.audience()),
                    new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)));
            return decoder;
        });
    }
}
