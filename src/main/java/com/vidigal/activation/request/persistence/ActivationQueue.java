package com.vidigal.activation.request.persistence;

import com.vidigal.activation.request.domain.ActivationStatus;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ActivationQueue {

    private static final String OWNED_LEASE_SQL = """
            WHERE id = :id AND status = 'PROCESSING' AND lease_token = :token
                AND lease_until > clock_timestamp()
            """;

    private static final String FINISH_SQL = """
            UPDATE activation_requests
            SET status = :status, lease_token = NULL, lease_until = NULL,
                failure_code = :failureCode, updated_at = clock_timestamp(), version = version + 1
            """ + OWNED_LEASE_SQL;

    private static final String RETRY_SQL = """
            UPDATE activation_requests
            SET status = :pendingStatus, lease_token = NULL, lease_until = NULL,
                next_attempt_at = clock_timestamp() + (:delayMillis * INTERVAL '1 millisecond'),
                failure_code = :failureCode, updated_at = clock_timestamp(), version = version + 1
            """ + OWNED_LEASE_SQL;

    private static final String DEFER_SQL = """
            UPDATE activation_requests
            SET status = :pendingStatus, lease_token = NULL, lease_until = NULL,
                next_attempt_at = clock_timestamp() + (:delayMillis * INTERVAL '1 millisecond'),
                attempt_count = attempt_count - 1, updated_at = clock_timestamp(), version = version + 1
            """ + OWNED_LEASE_SQL;

    private static final String ABANDON_SQL = """
            WITH abandoned AS (
                SELECT id FROM activation_requests
                WHERE ((status = 'PROCESSING' AND lease_until <= clock_timestamp()
                    AND (NOT :safeToRetryUncertain OR attempt_count >= :maxAttempts))
                    OR (status = 'PENDING' AND attempt_count >= :maxAttempts))
                    AND status IN (:failedSources)
                ORDER BY created_at, id
                LIMIT 1 FOR UPDATE SKIP LOCKED
            )
            UPDATE activation_requests request
            SET status = :failedStatus, lease_token = NULL, lease_until = NULL, next_attempt_at = NULL,
                failure_code = CASE WHEN request.status = 'PENDING' THEN 'ATTEMPTS_EXHAUSTED' ELSE 'RESULT_UNKNOWN' END,
                updated_at = clock_timestamp(), version = version + 1
            FROM abandoned WHERE request.id = abandoned.id
            """;

    private static final String RESERVE_SQL = """
            WITH candidate AS (
                SELECT id FROM activation_requests
                WHERE ((status = 'PENDING' AND next_attempt_at <= clock_timestamp())
                    OR (status = 'PROCESSING' AND lease_until <= clock_timestamp() AND :safeToRetryUncertain))
                    AND attempt_count < :maxAttempts
                ORDER BY created_at, id
                LIMIT 1 FOR UPDATE SKIP LOCKED
            )
            UPDATE activation_requests request
            SET status = :processingStatus, attempt_count = request.attempt_count + 1,
                lease_token = :token,
                lease_until = clock_timestamp() + (:leaseMillis * INTERVAL '1 millisecond'),
                next_attempt_at = NULL, updated_at = clock_timestamp(), version = version + 1
            FROM candidate WHERE request.id = candidate.id
            RETURNING request.id, request.lease_token, request.external_id, request.customer_id,
                request.product_code, request.correlation_id, request.attempt_count, request.trace_parent
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ActivationQueue(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReservedActivation> reserve(Duration leaseDuration, int maxAttempts, boolean safeToRetryUncertain) {
        var parameters = new MapSqlParameterSource()
                .addValue("maxAttempts", maxAttempts)
                .addValue("safeToRetryUncertain", safeToRetryUncertain)
                .addValue("processingStatus", ActivationStatus.PENDING.transitionTo(ActivationStatus.PROCESSING).name())
                .addValue("failedStatus", ActivationStatus.FAILED.name())
                .addValue("failedSources", Arrays.stream(ActivationStatus.values())
                        .filter(status -> status.canTransitionTo(ActivationStatus.FAILED)).map(Enum::name).toList())
                .addValue("token", UUID.randomUUID())
                .addValue("leaseMillis", leaseDuration.toMillis());
        jdbc.update(ABANDON_SQL, parameters);
        return jdbc.query(RESERVE_SQL, parameters, ActivationQueue::mapReservation).stream().findFirst();
    }

    public boolean complete(ReservedActivation reservation) {
        return finish(reservation, ActivationStatus.COMPLETED, null);
    }

    public boolean fail(ReservedActivation reservation, String failureCode) {
        return finish(reservation, ActivationStatus.FAILED, failureCode);
    }

    public boolean retry(ReservedActivation reservation, Duration delay, String failureCode) {
        var parameters = leaseParameters(reservation)
                .addValue("pendingStatus", ActivationStatus.PROCESSING.transitionTo(ActivationStatus.PENDING).name())
                .addValue("delayMillis", delay.toMillis())
                .addValue("failureCode", failureCode, Types.VARCHAR);
        return jdbc.update(RETRY_SQL, parameters) == 1;
    }

    public boolean defer(ReservedActivation reservation, Duration delay) {
        var parameters = leaseParameters(reservation)
                .addValue("pendingStatus", ActivationStatus.PROCESSING.transitionTo(ActivationStatus.PENDING).name())
                .addValue("delayMillis", delay.toMillis());
        return jdbc.update(DEFER_SQL, parameters) == 1;
    }

    private static ReservedActivation mapReservation(ResultSet result, int rowNumber) throws SQLException {
        return new ReservedActivation(result.getObject("id", UUID.class), result.getObject("lease_token", UUID.class),
                result.getString("external_id"), result.getString("customer_id"), result.getString("product_code"),
                result.getString("correlation_id"), result.getInt("attempt_count"), result.getString("trace_parent"));
    }

    private boolean finish(ReservedActivation reservation, ActivationStatus status, String failureCode) {
        var parameters = leaseParameters(reservation)
                .addValue("status", ActivationStatus.PROCESSING.transitionTo(status).name())
                .addValue("failureCode", failureCode, Types.VARCHAR);
        return jdbc.update(FINISH_SQL, parameters) == 1;
    }

    private MapSqlParameterSource leaseParameters(ReservedActivation reservation) {
        return new MapSqlParameterSource().addValue("id", reservation.requestId())
                .addValue("token", reservation.leaseToken());
    }
}
