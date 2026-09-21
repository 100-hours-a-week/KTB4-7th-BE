package com.memme.service.store;

import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;
import org.springframework.stereotype.Service;

@Service
public class AddressSearchService {

    private final AddressSearchClient addressSearchClient;

    public AddressSearchService(AddressSearchClient addressSearchClient) {
        this.addressSearchClient = addressSearchClient;
    }

    public AddressSearchResponse search(AddressSearchRequest request) {
        return addressSearchClient.search(request);
    }
}
