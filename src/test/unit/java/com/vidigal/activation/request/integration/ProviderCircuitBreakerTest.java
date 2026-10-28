package com.vidigal.activation.request.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ProviderCircuitBreakerTest {

    private static final ActivationOutcome TEMPORARY_FAILURE = new ActivationOutcome(
            ActivationOutcome.Kind.TRANSIENT_FAILURE, "PROVIDER_HTTP_503", Duration.ZERO);

    @Test
    void defersCallsAfterRepeatedProviderFailuresWithoutCallingTheProvider() {
        var properties = new CircuitBreakerProperties(2, 2, 50, Duration.ofSeconds(30), 1);
        var metrics = new SimpleMeterRegistry();
        var circuit = new ProviderCircuitBreaker(properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), metrics);
        var calls = new AtomicInteger();
        Supplier<ActivationOutcome> remoteCall = () -> {
            calls.incrementAndGet();
            return TEMPORARY_FAILURE;
        };

        circuit.execute(remoteCall);
        circuit.execute(remoteCall);
        ActivationOutcome deferred = circuit.execute(remoteCall);

        assertThat(deferred).isEqualTo(new ActivationOutcome(ActivationOutcome.Kind.DEFERRED,
                "PROVIDER_CIRCUIT_OPEN", Duration.ofSeconds(30)));
        assertThat(calls).hasValue(2);
    }

    @Test
    void allowsAProbeAfterTheOpenPeriodAndClosesAfterSuccess() {
        var now = new AtomicReference<>(Instant.EPOCH);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var metrics = new SimpleMeterRegistry();
        var circuit = new ProviderCircuitBreaker(new CircuitBreakerProperties(2, 2, 50, Duration.ofSeconds(30), 1),
                clock, metrics);
        circuit.execute(() -> TEMPORARY_FAILURE);
        circuit.execute(() -> TEMPORARY_FAILURE);
        now.set(Instant.EPOCH.plusSeconds(31));
        var success = new ActivationOutcome(ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO);

        assertThat(circuit.execute(() -> success)).isEqualTo(success);
        assertThat(circuit.execute(() -> success)).isEqualTo(success);
        assertThat(metrics.get("resilience4j.circuitbreaker.state").tag("state", "closed").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void businessRejectionsDoNotOpenTheCircuit() {
        var circuit = new ProviderCircuitBreaker(new CircuitBreakerProperties(2, 2, 50, Duration.ofSeconds(30), 1),
                Clock.systemUTC(), new SimpleMeterRegistry());
        var rejection = new ActivationOutcome(ActivationOutcome.Kind.PERMANENT_FAILURE, "PROVIDER_HTTP_400", Duration.ZERO);

        assertThat(circuit.execute(() -> rejection)).isEqualTo(rejection);
        assertThat(circuit.execute(() -> rejection)).isEqualTo(rejection);
        assertThat(circuit.execute(() -> rejection)).isEqualTo(rejection);
    }
}
