package com.vidigal.activation.security;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtDecoderIT {

    private static final String AUDIENCE = "product-activation-platform";
    private static RSAKey signingKey;

    @RegisterExtension
    static WireMockExtension issuer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private JwtDecoder decoder;

    @BeforeAll
    static void generateSigningKey() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-signing-key").generate();
    }

    @BeforeEach
    void configureIssuer() {
        issuer.stubFor(get("/.well-known/openid-configuration").willReturn(okJson("""
                {"issuer":"%s","jwks_uri":"%s/keys"}
                """.formatted(issuer.baseUrl(), issuer.baseUrl()))));
        issuer.stubFor(get("/keys").willReturn(okJson(new JWKSet(signingKey.toPublicJWK()).toString())));
        decoder = new JwtDecoderConfiguration().jwtDecoder(new SecurityProperties("jwt", issuer.baseUrl(), AUDIENCE));
    }

    @Test
    void acceptsTokenSignedByTheTrustedIssuer() throws JOSEException {
        String token = token(claims().build());

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("test-client");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "another-api")
    void rejectsTokenWithoutTheRequiredAudience(String audience) throws JOSEException {
        String token = token(claims().audience(audience).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() throws JOSEException {
        String token = token(claims().expirationTime(null).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidSubjects")
    void rejectsTokensWithoutABoundedConsumerIdentity(String subject) throws JOSEException {
        String token = token(claims().subject(subject).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    static Stream<String> invalidSubjects() {
        return Stream.of(null, "", " ", "a".repeat(256));
    }

    @ParameterizedTest
    @CsvSource({"disabled,https://issuer.example,api", "jwt,'',api", "jwt,https://issuer.example,''",
            "jwt,http://issuer.example,api", "jwt,ftp://issuer.example,api",
            "jwt,https://user@issuer.example,api", "jwt,https://issuer.example?token=invalid,api",
            "jwt,https://issuer.example#fragment,api", "jwt,relative-issuer,api"})
    void rejectsUnsafeSecurityConfiguration(String mode, String issuerUri, String audience) {
        assertThatThrownBy(() -> new SecurityProperties(mode, issuerUri, audience))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuerEvenWithATrustedSignature() throws JOSEException {
        String token = token(claims().issuer("https://untrusted.example").build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @CsvSource({"-120,-300", "600,300"})
    void rejectsExpiredOrNotYetValidTokens(long expirationOffset, long notBeforeOffset) throws JOSEException {
        Instant now = Instant.now();
        String token = token(claims().expirationTime(Date.from(now.plusSeconds(expirationOffset)))
                .notBeforeTime(Date.from(now.plusSeconds(notBeforeOffset))).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnUntrustedPrivateKey() throws JOSEException {
        RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();
        String token = token(claims().build(), untrustedKey);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshesPublicKeysWhenTheIssuerRotatesItsSigningKey() throws JOSEException {
        decoder.decode(token(claims().build()));
        RSAKey rotatedKey = new RSAKeyGenerator(2048).keyID("rotated-signing-key").generate();
        issuer.stubFor(get("/keys").willReturn(okJson(new JWKSet(rotatedKey.toPublicJWK()).toString())));
        String token = token(claims().subject("rotated-client").build(), rotatedKey);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("rotated-client");
    }

    @Test
    void defersIssuerDiscoveryUntilTheFirstTokenIsReceived() {
        assertThat(decoder).isNotNull();
        issuer.verify(0, getRequestedFor(urlMatching(".*")));
    }

    private JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer.baseUrl())
                .audience(AUDIENCE)
                .subject("test-client")
                .notBeforeTime(Date.from(now.minusSeconds(120)))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private String token(JWTClaimsSet claims) throws JOSEException {
        return token(claims, signingKey);
    }

    private String token(JWTClaimsSet claims, RSAKey key) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
