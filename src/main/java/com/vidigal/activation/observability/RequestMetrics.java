package com.vidigal.activation.observability;

import com.vidigal.activation.request.domain.ActivationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RequestMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestMetrics.class);
    private static final String COUNTS_SQL = """
            SELECT status, COUNT(*) AS request_count FROM activation_requests GROUP BY status
            """;

    private final JdbcTemplate jdbc;
    private final Map<ActivationStatus, AtomicLong> counts = new EnumMap<>(ActivationStatus.class);

    public RequestMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (ActivationStatus status : ActivationStatus.values()) {
            var count = new AtomicLong();
            counts.put(status, count);
            Gauge.builder("activation.requests", count, AtomicLong::doubleValue)
                    .description("Persisted activation requests by current status")
                    .tag("status", status.name())
                    .register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${activation.observability.refresh-interval}")
    public void refresh() {
        try {
            var snapshot = new EnumMap<ActivationStatus, Long>(ActivationStatus.class);
            RowCallbackHandler collectCount = row ->
                    snapshot.put(ActivationStatus.valueOf(row.getString("status")), row.getLong("request_count"));
            jdbc.query(COUNTS_SQL, collectCount);
            counts.forEach((status, count) -> count.set(snapshot.getOrDefault(status, 0L)));
        } catch (DataAccessException exception) {
            LOGGER.warn("Request metrics refresh failed; previous sample retained; exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
