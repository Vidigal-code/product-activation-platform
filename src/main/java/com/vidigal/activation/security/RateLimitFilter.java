package com.vidigal.activation.security;

import com.vidigal.activation.web.ApiProblem;
import com.vidigal.activation.web.ProblemResponseWriter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static com.vidigal.activation.request.api.ActivationRequestController.PATH;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String METRIC_NAME = "activation.rate.limit";
    private static final String RESULT_TAG = "result";
    private final ConsumerRateLimiter limiter;
    private final ConsumerIdentity identity;
    private final ProblemResponseWriter problems;
    private final MeterRegistry metrics;

    public RateLimitFilter(ConsumerRateLimiter limiter, ConsumerIdentity identity,
            ProblemResponseWriter problems, MeterRegistry metrics) {
        this.limiter = limiter;
        this.identity = identity;
        this.problems = problems;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        return !path.equals(PATH) && !path.startsWith(PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ConsumerRateLimiter.Decision decision;
        try {
            decision = limiter.acquire(identity.currentId());
        } catch (DataAccessException exception) {
            LOG.error("Rate limit persistence unavailable; type={}", exception.getClass().getSimpleName());
            metrics.counter(METRIC_NAME, RESULT_TAG, "UNAVAILABLE").increment();
            problems.write(request, response, ApiProblem.INTERNAL_ERROR);
            return;
        }
        if (!decision.allowed()) {
            metrics.counter(METRIC_NAME, RESULT_TAG, "REJECTED").increment();
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            problems.write(request, response, ApiProblem.RATE_LIMITED);
            return;
        }
        metrics.counter(METRIC_NAME, RESULT_TAG, "ALLOWED").increment();
        chain.doFilter(request, response);
    }
}
