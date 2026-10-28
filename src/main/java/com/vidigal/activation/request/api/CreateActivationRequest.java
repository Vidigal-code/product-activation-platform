package com.vidigal.activation.request.api;

import com.vidigal.activation.request.domain.ActivationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import static com.vidigal.activation.request.api.RequestInputConstraints.IDENTIFIER_LIMIT;
import static com.vidigal.activation.request.api.RequestInputConstraints.IDENTIFIER_PATTERN;
import static com.vidigal.activation.request.api.RequestInputConstraints.PRODUCT_LIMIT;

public record CreateActivationRequest(
        @NotBlank(message = REQUIRED) @Size(max = IDENTIFIER_LIMIT, message = SIZE_MESSAGE)
        @Pattern(regexp = IDENTIFIER_PATTERN, message = FORMAT_MESSAGE) String externalId,
        @NotBlank(message = REQUIRED) @Size(max = IDENTIFIER_LIMIT, message = SIZE_MESSAGE)
        @Pattern(regexp = IDENTIFIER_PATTERN, message = FORMAT_MESSAGE) String customerId,
        @NotBlank(message = REQUIRED) @Size(max = PRODUCT_LIMIT, message = SIZE_MESSAGE)
        @Pattern(regexp = IDENTIFIER_PATTERN, message = FORMAT_MESSAGE) String productCode) {

    private static final String REQUIRED = "é obrigatório";
    private static final String SIZE_MESSAGE = "deve ter no máximo {max} caracteres";
    private static final String FORMAT_MESSAGE = "deve iniciar com letra ou número e conter apenas letras, números, ponto, sublinhado, dois-pontos ou hífen";

    public ActivationCommand toCommand() {
        return new ActivationCommand(externalId, customerId, productCode);
    }
}
