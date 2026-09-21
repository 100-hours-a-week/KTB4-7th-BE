package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;
import com.memme.exception.AddressSearchFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JusoAddressSearchClientTest {

    private MockRestServiceServer server;
    private JusoAddressSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JusoAddressSearchClient(builder, "test-confirmation-key");
    }

    @Test
    void 행정안전부_주소_응답을_우리_주소_응답으로_변환한다() {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("GET");
                    assertThat(request.getURI().getPath()).isEqualTo("/addrlink/addrLinkApi.do");
                    assertThat(request.getURI().getQuery()).contains(
                            "confmKey=test-confirmation-key",
                            "currentPage=2",
                            "countPerPage=5",
                            "keyword=판교역로",
                            "resultType=json"
                    );
                })
                .andRespond(withSuccess("""
                        {
                          "results": {
                            "common": {
                              "totalCount": "12",
                              "currentPage": "2",
                              "countPerPage": "5",
                              "errorCode": "0",
                              "errorMessage": "정상"
                            },
                            "juso": [
                              {
                                "zipNo": "13494",
                                "roadAddr": "경기도 성남시 분당구 판교역로 166",
                                "jibunAddr": "경기도 성남시 분당구 백현동 532"
                              }
                            ]
                          }
                        }
                        """, APPLICATION_JSON));

        AddressSearchResponse response = client.search(new AddressSearchRequest("판교역로", "2", 5));

        assertThat(response.addresses()).containsExactly(new AddressSearchResponse.Address(
                "13494",
                "경기도 성남시 분당구 판교역로 166",
                "경기도 성남시 분당구 백현동 532"
        ));
        assertThat(response.nextCursor()).isEqualTo("3");
        server.verify();
    }

    @Test
    void 행정안전부가_오류_코드를_반환하면_주소_검색_실패_예외를_던진다() {
        server.expect(request -> {
                })
                .andRespond(withSuccess("""
                        {
                          "results": {
                            "common": {
                              "totalCount": "0",
                              "currentPage": "1",
                              "countPerPage": "10",
                              "errorCode": "E0001",
                              "errorMessage": "승인되지 않은 KEY 입니다."
                            },
                            "juso": []
                          }
                        }
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> client.search(new AddressSearchRequest("판교역로", null, null)))
                .isInstanceOf(AddressSearchFailedException.class);

        server.verify();
    }

    @Test
    void 커서가_비어_있으면_첫_페이지를_조회한다() {
        server.expect(request -> assertThat(request.getURI().getQuery()).contains("currentPage=1"))
                .andRespond(withSuccess("""
                        {
                          "results": {
                            "common": {
                              "totalCount": "0",
                              "currentPage": "1",
                              "countPerPage": "10",
                              "errorCode": "0",
                              "errorMessage": "정상"
                            },
                            "juso": []
                          }
                        }
                        """, APPLICATION_JSON));

        AddressSearchResponse response = client.search(new AddressSearchRequest("판교역로", "", null));

        assertThat(response.addresses()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        server.verify();
    }
}
