package com.vidigal.activation.request.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("activation.worker")
public record WorkerProperties(@NotNull @DurationMin(millis = 1) Duration leaseDuration) {
}
