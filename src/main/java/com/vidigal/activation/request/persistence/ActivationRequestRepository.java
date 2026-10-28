package com.vidigal.activation.request.persistence;

import com.vidigal.activation.request.domain.ActivationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Optional<ActivationRequest> findByExternalId(String externalId);
}
