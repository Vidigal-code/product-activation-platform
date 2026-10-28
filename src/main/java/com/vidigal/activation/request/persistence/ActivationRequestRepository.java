package com.vidigal.activation.request.persistence;

import com.vidigal.activation.request.domain.ActivationRequest;
import com.vidigal.activation.request.domain.ActivationStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID>,
        JpaSpecificationExecutor<ActivationRequest> {
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Optional<ActivationRequest> findByExternalId(String externalId);

    Optional<ActivationRequest> findByIdAndConsumerId(UUID id, String consumerId);

    default Page<ActivationRequest> findOwned(String consumerId, ActivationStatus status,
            String customerId, Pageable pageable) {
        return findAll((root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("consumerId"), consumerId));
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customerId"), customerId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }, pageable);
    }
}
