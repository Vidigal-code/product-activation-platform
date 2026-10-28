package com.vidigal.activation.request.application;

import com.vidigal.activation.request.integration.ActivationOutcome;
import com.vidigal.activation.request.integration.ActivationPayload;
import com.vidigal.activation.request.integration.ActivationProvider;
import com.vidigal.activation.request.integration.ProviderProperties;
import com.vidigal.activation.request.integration.RetryPolicy;
import com.vidigal.activation.observability.ActivationTracing;
import com.vidigal.activation.request.persistence.ActivationQueue;
import com.vidigal.activation.request.persistence.ReservedActivation;
import com.vidigal.activation.web.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class ActivationWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivationWorker.class);
    private final ActivationQueue queue;
    private final ActivationProvider provider;
    private final WorkerProperties workerProperties;
    private final ProviderProperties providerProperties;
    private final ActivationTracing tracing;
    private final RetryPolicy retryPolicy;
    private final MeterRegistry metrics;

    public ActivationWorker(ActivationQueue queue, ActivationProvider provider, WorkerProperties workerProperties,
            ProviderProperties providerProperties, ActivationTracing tracing, RetryPolicy retryPolicy,
            MeterRegistry metrics) {
        this.queue = queue;
        this.provider = provider;
        this.workerProperties = workerProperties;
        this.providerProperties = providerProperties;
        this.tracing = tracing;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
    }

    public boolean processNext() {
        var next = queue.reserve(workerProperties.leaseDuration(), retryPolicy.maxAttempts(),
                providerProperties.idempotencySupported());
        if (next.isEmpty()) {
            return false;
        }
        var reservation = next.orElseThrow();
        tracing.runWithProcessingSpan(reservation.traceParent(), () -> process(reservation));
        return true;
    }

    private void process(ReservedActivation reservation) {
        try (var context = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, reservation.correlationId())) {
            var sample = Timer.start(metrics);
            var outcome = activate(reservation);
            sample.stop(metrics.timer("activation.provider.calls", "outcome", outcome.kind().name()));
            recordOutcome(reservation, outcome);
        }
    }

    private ActivationOutcome activate(ReservedActivation reservation) {
        try {
            return provider.activate(new ActivationPayload(reservation.requestId(), reservation.externalId(),
                    reservation.customerId(), reservation.productCode(), reservation.correlationId()));
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected provider error requestId={} exceptionType={}",
                    reservation.requestId(), exception.getClass().getSimpleName());
            return new ActivationOutcome(ActivationOutcome.Kind.UNCERTAIN, "PROVIDER_UNEXPECTED_ERROR", Duration.ZERO);
        }
    }

    private void recordOutcome(ReservedActivation reservation, ActivationOutcome outcome) {
        boolean updated;
        String result;
        if (outcome.kind() == ActivationOutcome.Kind.SUCCESS) {
            updated = queue.complete(reservation);
            result = "COMPLETED";
        } else if (outcome.kind() == ActivationOutcome.Kind.DEFERRED) {
            updated = queue.defer(reservation, outcome.retryAfter());
            result = "DEFERRED";
        } else if (retryPolicy.canRetry(outcome.kind(), reservation.attemptCount(),
                providerProperties.idempotencySupported())) {
            updated = queue.retry(reservation,
                    retryPolicy.delayAfter(reservation.attemptCount(), outcome.retryAfter()), outcome.code());
            result = "RETRY_SCHEDULED";
        } else {
            updated = queue.fail(reservation, outcome.code());
            result = "FAILED";
        }
        if (!updated) {
            result = "STALE_LEASE";
            LOGGER.warn("Discarded result from expired reservation requestId={}", reservation.requestId());
        }
        metrics.counter("activation.worker.results", "result", result).increment();
        LOGGER.info("Activation processed requestId={} attempt={} result={} providerCode={}",
                reservation.requestId(), reservation.attemptCount(), result, outcome.code());
    }
}
