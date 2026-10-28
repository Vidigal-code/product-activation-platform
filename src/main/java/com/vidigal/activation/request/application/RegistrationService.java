package com.vidigal.activation.request.application;

import com.vidigal.activation.request.domain.ActivationCommand;
import com.vidigal.activation.request.domain.ActivationRequest;
import com.vidigal.activation.request.persistence.ActivationRequestRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class RegistrationService {

    private static final String EXTERNAL_ID_CONSTRAINT = "uk_activation_external_id";

    private final ActivationRequestRepository repository;
    private final TransactionTemplate transaction;

    public RegistrationService(ActivationRequestRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Registration register(ActivationCommand command, String correlationId) {
        var existing = repository.findByExternalId(command.externalId());
        if (existing.isPresent()) {
            return repeated(existing.orElseThrow(), command);
        }
        try {
            var request = transaction.execute(status -> repository.saveAndFlush(new ActivationRequest(command, correlationId)));
            return new Registration(request, true);
        } catch (DataIntegrityViolationException exception) {
            if (!isExternalIdConflict(exception)) {
                throw exception;
            }
            var winner = repository.findByExternalId(command.externalId()).orElseThrow(() -> exception);
            return repeated(winner, command);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public ActivationRequest get(UUID id) {
        return repository.findById(id).orElseThrow(RequestNotFoundException::new);
    }

    private Registration repeated(ActivationRequest existing, ActivationCommand command) {
        if (!existing.getCustomerId().equals(command.customerId())
                || !existing.getProductCode().equals(command.productCode())) {
            throw new DuplicateRequestException();
        }
        return new Registration(existing, false);
    }

    private boolean isExternalIdConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && EXTERNAL_ID_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    public record Registration(ActivationRequest request, boolean created) {
    }
}
