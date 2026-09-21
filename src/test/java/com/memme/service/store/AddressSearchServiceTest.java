package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;
import com.memme.exception.AddressSearchFailedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressSearchServiceTest {

    @Mock
    private AddressSearchClient addressSearchClient;

    @InjectMocks
    private AddressSearchService addressSearchService;

    @Test
    void 주소_검색_Client의_결과를_반환한다() {
        AddressSearchRequest request = new AddressSearchRequest("판교역로", null, 10);
        AddressSearchResponse expected = new AddressSearchResponse(
                List.of(new AddressSearchResponse.Address(
                        "13494",
                        "경기도 성남시 분당구 판교역로 166",
                        "경기도 성남시 분당구 백현동 532"
                )),
                null
        );
        when(addressSearchClient.search(request)).thenReturn(expected);

        AddressSearchResponse response = addressSearchService.search(request);

        assertThat(response).isEqualTo(expected);
        verify(addressSearchClient).search(request);
    }

    @Test
    void 주소_검색_Client_실패를_그대로_전달한다() {
        AddressSearchRequest request = new AddressSearchRequest("판교역로", null, 10);
        when(addressSearchClient.search(request)).thenThrow(new AddressSearchFailedException());

        assertThatThrownBy(() -> addressSearchService.search(request))
                .isInstanceOf(AddressSearchFailedException.class);
    }
}
