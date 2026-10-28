package com.vidigal.activation.request.api;

import com.vidigal.activation.request.application.RegistrationService;
import com.vidigal.activation.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ActivationRequestController.PATH)
public class ActivationRequestController {

    public static final String PATH = "/api/v1/requests";

    private final RegistrationService registrationService;

    public ActivationRequestController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequest input) {
        var registration = registrationService.register(input.toCommand(), MDC.get(CorrelationIdFilter.MDC_KEY));
        var response = ActivationRequestResponse.from(registration.request());
        if (!registration.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create(PATH + "/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ActivationRequestResponse get(@PathVariable UUID id) {
        return ActivationRequestResponse.from(registrationService.get(id));
    }
}
