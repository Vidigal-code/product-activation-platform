package com.vidigal.activation.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "activation_requests")
public class ActivationRequest {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100, updatable = false)
    private String externalId;

    @Column(nullable = false, length = 100, updatable = false)
    private String customerId;

    @Column(nullable = false, length = 80, updatable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActivationStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 128, updatable = false)
    private String correlationId;

    @Column(nullable = false)
    private int attemptCount;

    private Instant nextAttemptAt;
    private UUID leaseToken;
    private Instant leaseUntil;

    @Column(length = 64)
    private String failureCode;

    @Version
    private Long version;

    protected ActivationRequest() {
    }

    public ActivationRequest(ActivationCommand command, String correlationId) {
        this.id = UUID.randomUUID();
        this.externalId = command.externalId();
        this.customerId = command.customerId();
        this.productCode = command.productCode();
        this.correlationId = correlationId;
        this.status = ActivationStatus.PENDING;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getCustomerId() { return customerId; }
    public String getProductCode() { return productCode; }
    public ActivationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCorrelationId() { return correlationId; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public UUID getLeaseToken() { return leaseToken; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getFailureCode() { return failureCode; }
}
