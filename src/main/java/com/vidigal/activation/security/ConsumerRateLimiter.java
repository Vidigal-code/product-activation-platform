package com.vidigal.activation.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ConsumerRateLimiter {
    private static final String ACQUIRE_SQL = """
            INSERT INTO api_rate_limits AS quota (consumer_id, window_started_at, request_count)
            VALUES (:consumerId, statement_timestamp(), 1)
            ON CONFLICT (consumer_id) DO UPDATE SET
                request_count = CASE
                    WHEN quota.window_started_at + (:windowMillis * INTERVAL '1 millisecond') <= statement_timestamp()
                    THEN 1 ELSE LEAST(quota.request_count + 1, :limit + 1) END,
                window_started_at = CASE
                    WHEN quota.window_started_at + (:windowMillis * INTERVAL '1 millisecond') <= statement_timestamp()
                    THEN statement_timestamp() ELSE quota.window_started_at END
            RETURNING request_count <= :limit AS allowed,
                GREATEST(0, :limit - request_count) AS remaining,
                GREATEST(1, CEIL(EXTRACT(EPOCH FROM
                    window_started_at + (:windowMillis * INTERVAL '1 millisecond') - statement_timestamp()))) AS retry_seconds
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RateLimitProperties properties;

    public ConsumerRateLimiter(NamedParameterJdbcTemplate jdbc, RateLimitProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public Decision acquire(String consumerId) {
        var parameters = new MapSqlParameterSource("consumerId", consumerId)
                .addValue("limit", properties.maxRequests())
                .addValue("windowMillis", properties.window().toMillis());
        return jdbc.queryForObject(ACQUIRE_SQL, parameters, (row, index) ->
                new Decision(row.getBoolean("allowed"), row.getInt("remaining"), row.getLong("retry_seconds")));
    }

    public int removeExpiredWindows() {
        return jdbc.update("""
                DELETE FROM api_rate_limits WHERE consumer_id IN (
                    SELECT consumer_id FROM api_rate_limits
                    WHERE window_started_at + (:windowMillis * INTERVAL '1 millisecond') <= statement_timestamp()
                    ORDER BY window_started_at LIMIT 1000 FOR UPDATE SKIP LOCKED
                )
                """, new MapSqlParameterSource("windowMillis", properties.window().toMillis()));
    }

    public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
    }
}
