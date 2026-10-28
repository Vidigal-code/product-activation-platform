package com.vidigal.activation.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vidigal.activation.request.domain.ActivationCommand;
import com.vidigal.activation.request.domain.ActivationStatus;
import com.vidigal.activation.request.integration.ActivationOutcome;
import com.vidigal.activation.request.integration.ActivationProvider;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "activation.security.mode=local",
        "activation.worker.enabled=true",
        "activation.worker.poll-interval=25ms"
})
@Import(ActivationWorkerIT.Database.class)
class ActivationSchedulerIT {

    @Autowired
    private RegistrationService registrations;

    @MockitoBean
    private ActivationProvider provider;

    @Test
    void processesPersistedRequestsWithoutAnHttpThreadOrManualWorkerTrigger() {
        when(provider.activate(any())).thenReturn(new ActivationOutcome(
                ActivationOutcome.Kind.SUCCESS, "PROVIDER_SUCCESS", Duration.ZERO));
        var registration = registrations.register(
                new ActivationCommand("EXT-" + UUID.randomUUID(), "CUST-001", "INTERNET-500"), "scheduled-request");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(registrations.get(registration.request().getId()).getStatus())
                        .isEqualTo(ActivationStatus.COMPLETED));
    }
}
