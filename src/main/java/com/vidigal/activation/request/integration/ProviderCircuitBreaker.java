package com.vidigal.activation.request.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ProviderCircuitBreaker {

    private final CircuitBreaker circuit;
    private final Duration openDuration;

    public ProviderCircuitBreaker(CircuitBreakerProperties properties, Clock clock, MeterRegistry metrics) {
        this.openDuration = properties.openDuration();
        CircuitBreakerConfig configuration = CircuitBreakerConfig.custom()
                .clock(clock)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.openDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls())
                .recordResult(result -> result instanceof ActivationOutcome outcome
                        && (outcome.kind() == ActivationOutcome.Kind.TRANSIENT_FAILURE
                        || outcome.kind() == ActivationOutcome.Kind.UNCERTAIN))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(configuration);
        this.circuit = registry.circuitBreaker("activationProvider");
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(metrics);
    }

    public ActivationOutcome execute(Supplier<ActivationOutcome> remoteCall) {
        try {
            return circuit.executeSupplier(remoteCall);
        } catch (CallNotPermittedException exception) {
            return new ActivationOutcome(ActivationOutcome.Kind.DEFERRED, "PROVIDER_CIRCUIT_OPEN", openDuration);
        }
    }
}
