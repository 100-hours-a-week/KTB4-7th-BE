package com.memme.controller.store;

import com.memme.dto.common.ApiResponse;
import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;
import com.memme.service.store.AddressSearchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/addresses")
public class AddressSearchController {

    private final AddressSearchService addressSearchService;

    public AddressSearchController(AddressSearchService addressSearchService) {
        this.addressSearchService = addressSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<AddressSearchResponse>> search(
            @Valid @ModelAttribute AddressSearchRequest request
    ) {
        AddressSearchResponse response = addressSearchService.search(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("조회에 성공했습니다.", response));
    }
}
