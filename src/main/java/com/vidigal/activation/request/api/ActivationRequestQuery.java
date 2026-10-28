package com.vidigal.activation.request.api;

import com.vidigal.activation.request.application.RequestSearchCriteria;
import com.vidigal.activation.request.application.RequestQueryException;
import com.vidigal.activation.request.domain.ActivationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import static com.vidigal.activation.request.api.RequestInputConstraints.IDENTIFIER_LIMIT;
import static com.vidigal.activation.request.api.RequestInputConstraints.IDENTIFIER_PATTERN;

public record ActivationRequestQuery(ActivationStatus status,
        @Size(max = IDENTIFIER_LIMIT) @Pattern(regexp = IDENTIFIER_PATTERN) String customerId,
        @Min(0) Integer page, @Min(1) Integer size) {

    public RequestSearchCriteria toCriteria(PaginationProperties properties) {
        int selectedPage = page == null ? 0 : page;
        int selectedSize = size == null ? properties.defaultSize() : size;
        if (selectedPage > properties.maxPage() || selectedSize > properties.maxSize()) {
            throw new RequestQueryException();
        }
        return new RequestSearchCriteria(status, customerId, selectedPage, selectedSize);
    }
}
