package com.vidigal.activation.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import static com.vidigal.activation.request.api.ActivationRequestController.PATH;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final RequestBodyProperties properties;
    private final ProblemResponseWriter problems;

    public RequestBodyLimitFilter(RequestBodyProperties properties, ProblemResponseWriter problems) {
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(request.getContextPath() + PATH) || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > properties.maxBodyBytes()) {
            problems.write(request, response, ApiProblem.PAYLOAD_TOO_LARGE);
            return;
        }
        var bounded = new ContentCachingRequestWrapper(request, properties.maxBodyBytes()) {
            @Override
            protected void handleContentOverflow(int contentCacheLimit) {
                throw new PayloadTooLargeException();
            }
        };
        try {
            chain.doFilter(bounded, response);
        } catch (PayloadTooLargeException exception) {
            problems.write(request, response, ApiProblem.PAYLOAD_TOO_LARGE);
        }
    }
}
