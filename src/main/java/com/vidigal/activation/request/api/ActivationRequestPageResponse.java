package com.vidigal.activation.request.api;

import com.vidigal.activation.request.domain.ActivationRequest;
import java.util.List;
import org.springframework.data.domain.Page;

public record ActivationRequestPageResponse(List<ActivationRequestResponse> items, int page, int size,
        long totalElements, int totalPages, boolean hasNext) {

    public static ActivationRequestPageResponse from(Page<ActivationRequest> requests) {
        return new ActivationRequestPageResponse(requests.getContent().stream().map(ActivationRequestResponse::from).toList(),
                requests.getNumber(), requests.getSize(), requests.getTotalElements(), requests.getTotalPages(),
                requests.hasNext());
    }
}
