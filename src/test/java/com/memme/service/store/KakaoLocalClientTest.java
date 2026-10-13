package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class KakaoLocalClientTest {

    private MockRestServiceServer server;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder, "test-rest-api-key");
    }

    @Test
    void 도로명주소를_좌표로_변환하고_카카오가_요구하는_인증헤더를_보낸다() {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("GET");
                    assertThat(request.getURI().getPath()).isEqualTo("/v2/local/search/address.json");
                    assertThat(request.getURI().getQuery()).contains("query=서울특별시 강남구 테헤란로 123");
                    assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("KakaoAK test-rest-api-key");
                })
                .andRespond(withSuccess("""
                        {
                          "meta": {"total_count": 1},
                          "documents": [{
                            "address": {"x": "127.027610", "y": "37.498095"},
                            "road_address": {"x": "127.027610", "y": "37.498095"}
                          }]
                        }
                        """, APPLICATION_JSON));

        assertThat(client.findCoordinates("서울특별시 강남구 테헤란로 123"))
                .contains(new StoreCoordinates(new BigDecimal("37.498095"), new BigDecimal("127.027610")));
        server.verify();
    }

    @Test
    void 도로명주소가_없으면_지번주소_좌표를_사용한다() {
        server.expect(request -> {})
                .andRespond(withSuccess("""
                        {
                          "meta": {"total_count": 1},
                          "documents": [{"address": {"x": "126.8646", "y": "37.4786"}, "road_address": null}]
                        }
                        """, APPLICATION_JSON));

        assertThat(client.findCoordinates("서울특별시 강남구 테스트 주소"))
                .contains(new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646")));
        server.verify();
    }

    @Test
    void 주소_검색결과가_없으면_빈값을_반환한다() {
        server.expect(request -> {})
                .andRespond(withSuccess("{\"meta\":{\"total_count\":0},\"documents\":[]}", APPLICATION_JSON));

        assertThat(client.findCoordinates("검색 결과 없는 주소")).isEmpty();
        server.verify();
    }

    @Test
    void 카카오_인증실패는_HTTP_예외로_전달한다() {
        server.expect(request -> {
                    assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("KakaoAK test-rest-api-key");
                })
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.findCoordinates("서울특별시 강남구 테헤란로 123"))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    @Test
    void 좌표의_행정동을_찾을_때_법정동을_제외하고_H코드만_서울_행정동코드로_정규화한다() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v2/local/geo/coord2regioncode.json");
                    assertThat(request.getURI().getQuery()).contains("x=126.8646", "y=37.4786");
                    assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("KakaoAK test-rest-api-key");
                })
                .andRespond(withSuccess("""
                        {
                          "meta": {"total_count": 2},
                          "documents": [
                            {"region_type":"B", "code":"1168010100", "address_name":"서울특별시 강남구 역삼동"},
                            {"region_type":"H", "code":"1168064000", "address_name":"서울특별시 강남구 역삼1동"}
                          ]
                        }
                        """, APPLICATION_JSON));

        assertThat(client.findAdministrativeDistrict(new BigDecimal("126.8646"), new BigDecimal("37.4786")))
                .contains(new AdministrativeDistrict("11680640", "서울특별시 강남구 역삼1동"));
        server.verify();
    }

    @Test
    void 행정동_H코드가_없으면_법정동을_대신_사용하지_않는다() {
        server.expect(request -> {})
                .andRespond(withSuccess("""
                        {
                          "meta": {"total_count": 1},
                          "documents": [
                            {"region_type":"B", "code":"1168010100", "address_name":"서울특별시 강남구 역삼동"}
                          ]
                        }
                        """, APPLICATION_JSON));

        assertThat(client.findAdministrativeDistrict(new BigDecimal("126.8646"), new BigDecimal("37.4786"))).isEmpty();
        server.verify();
    }

    @Test
    void 카카오키가_없으면_외부호출없이_빈값을_반환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalClient clientWithoutKey = new KakaoLocalClient(builder, " ");

        assertThat(clientWithoutKey.findCoordinates("서울특별시 강남구 테헤란로 123")).isEmpty();
        assertThat(clientWithoutKey.findAdministrativeDistrict(
                new BigDecimal("126.8646"), new BigDecimal("37.4786")
        )).isEmpty();
        emptyServer.verify();
    }
}
