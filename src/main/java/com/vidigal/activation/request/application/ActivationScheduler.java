package com.vidigal.activation.request.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "activation.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ActivationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivationScheduler.class);
    private final ActivationWorker worker;

    public ActivationScheduler(ActivationWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${activation.worker.poll-interval}")
    public void processNext() {
        try {
            worker.processNext();
        } catch (RuntimeException exception) {
            LOGGER.error("Worker iteration failed; persistent lease will be recovered; exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
