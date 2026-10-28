package com.vidigal.activation.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ActivationTracing {

    private static final String TRACE_PARENT = "traceparent";
    private final Tracer tracer;
    private final Propagator propagator;

    public ActivationTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public String currentTraceParent() {
        Span current = tracer.currentSpan();
        if (current == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(current.context(), carrier, Map::put);
        return carrier.get(TRACE_PARENT);
    }

    public void runWithProcessingSpan(String traceParent, Runnable work) {
        Span.Builder builder = traceParent == null || traceParent.isBlank()
                ? tracer.spanBuilder().setNoParent()
                : propagator.extract(Map.of(TRACE_PARENT, traceParent), Map::get);
        Span span = builder.name("activation.process").kind(Span.Kind.CONSUMER).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            work.run();
        } catch (RuntimeException exception) {
            span.tag("error.type", exception.getClass().getName());
            span.event("processing.failed");
            throw exception;
        } finally {
            span.end();
        }
    }
}
