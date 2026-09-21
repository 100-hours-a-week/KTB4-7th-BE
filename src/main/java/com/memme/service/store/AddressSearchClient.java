package com.memme.service.store;

import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;

public interface AddressSearchClient {

    AddressSearchResponse search(AddressSearchRequest request);
}
