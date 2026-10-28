package com.vidigal.activation.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimitMaintenance {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitMaintenance.class);
    private final ConsumerRateLimiter limiter;

    public RateLimitMaintenance(ConsumerRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Scheduled(fixedDelayString = "${activation.rate-limit.cleanup-interval}")
    public void removeExpiredWindows() {
        try {
            limiter.removeExpiredWindows();
        } catch (DataAccessException exception) {
            LOGGER.warn("Rate limit cleanup failed; next iteration will retry; exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
