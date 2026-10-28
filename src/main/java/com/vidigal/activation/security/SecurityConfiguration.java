package com.vidigal.activation.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.http.HttpMethod;
import static com.vidigal.activation.request.api.ActivationRequestController.PATH;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final String HEALTH_PATH = "/actuator/health/**";
    private static final String METRICS_PATH = "/actuator/prometheus";

    @Bean
    @ConditionalOnProperty(name = "activation.security.mode", havingValue = SecurityProperties.JWT_MODE,
            matchIfMissing = true)
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http, JwtDecoder decoder,
            AuthenticationProblemHandler problems) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, HEALTH_PATH).permitAll()
                        .requestMatchers(HttpMethod.POST, PATH).hasAuthority("SCOPE_requests:write")
                        .requestMatchers(HttpMethod.GET, PATH + "/*").hasAuthority("SCOPE_requests:read")
                        .requestMatchers(HttpMethod.GET, METRICS_PATH).hasAuthority("SCOPE_monitoring:read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder))
                        .authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "activation.security.mode", havingValue = SecurityProperties.LOCAL_MODE)
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PATH, PATH + "/*", HEALTH_PATH, METRICS_PATH).permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
