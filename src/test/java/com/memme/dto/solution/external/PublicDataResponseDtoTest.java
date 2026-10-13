package com.memme.dto.solution.external;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PublicDataResponseDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void readsSeoulEstimatedSales() {
        SeoulEstimatedSalesResponse response = jsonMapper.readValue("""
                {"VwsmAdstrdSelngW":{"list_total_count":1,
                 "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                 "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                         "SVC_INDUTY_CD":"CS100010","THSMON_SELNG_AMT":1200000,
                         "THSMON_SELNG_CO":300,"TMZON_11_14_SELNG_AMT":450000,
                         "UNUSED":"ignored"}]}}
                """, SeoulEstimatedSalesResponse.class);

        assertThat(response.data().result().code()).isEqualTo("INFO-000");
        assertThat(response.data().row().getFirst().quarterCode()).isEqualTo("20253");
        assertThat(response.data().row().getFirst().salesAmount()).isEqualTo(1200000L);
        assertThat(response.data().row().getFirst().salesCount()).isEqualTo(300L);
        assertThat(response.data().row().getFirst().salesFrom11To14()).isEqualTo(450000L);
    }

    @Test
    void readsSeoulStoreCount() {
        SeoulStoreCountResponse response = jsonMapper.readValue("""
                {"VwsmAdstrdStorW":{"list_total_count":1,
                 "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                 "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                         "SVC_INDUTY_CD":"CS100010","SIMILR_INDUTY_STOR_CO":40,
                         "OPBIZ_STOR_CO":3,"CLSBIZ_STOR_CO":2}]}}
                """, SeoulStoreCountResponse.class);

        assertThat(response.data().row().getFirst().storeCount()).isEqualTo(40L);
        assertThat(response.data().row().getFirst().openCount()).isEqualTo(3L);
    }

    @Test
    void readsSeoulFootTraffic() {
        SeoulFootTrafficResponse response = jsonMapper.readValue("""
                {"VwsmAdstrdFlpopW":{"list_total_count":1,
                 "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                 "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                         "TOT_FLPOP_CO":10000,"TMZON_11_14_FLPOP_CO":2600}]}}
                """, SeoulFootTrafficResponse.class);

        assertThat(response.data().row().getFirst().totalCount()).isEqualTo(10000L);
        assertThat(response.data().row().getFirst().from11To14()).isEqualTo(2600L);
    }

    @Test
    void preservesSeoulApiErrorWithoutRows() {
        SeoulEstimatedSalesResponse response = jsonMapper.readValue("""
                {"RESULT":{"CODE":"INFO-100","MESSAGE":"인증키가 유효하지 않습니다"}}
                """, SeoulEstimatedSalesResponse.class);

        assertThat(response.result().code()).isEqualTo("INFO-100");
        assertThat(response.data()).isNull();
    }

    @Test
    void readsForecastItemsIncludingTextualPrecipitation() {
        KmaVillageForecastResponse response = jsonMapper.readValue("""
                {"response": {
                  "header": {"resultCode":"00", "resultMsg":"NORMAL_SERVICE"},
                  "body": {"dataType":"JSON", "numOfRows":1000, "pageNo":1, "totalCount":2,
                    "items": {"item": [
                      {"baseDate":"20261007", "baseTime":"1100", "category":"PCP",
                       "fcstDate":"20261007", "fcstTime":"1500", "fcstValue":"강수없음",
                       "nx":58, "ny":125},
                      {"baseDate":"20261007", "baseTime":"1100", "category":"POP",
                       "fcstDate":"20261007", "fcstTime":"1500", "fcstValue":"60",
                       "nx":58, "ny":125}
                    ]}}
                  }}
                """, KmaVillageForecastResponse.class);

        assertThat(response.response().header().resultCode()).isEqualTo("00");
        assertThat(response.response().body().totalCount()).isEqualTo(2);
        assertThat(response.response().body().items().item()).hasSize(2);
        assertThat(response.response().body().items().item().getFirst().fcstValue()).isEqualTo("강수없음");
        assertThat(response.response().body().items().item().get(1).nx()).isEqualTo(58);
    }

    @Test
    void preservesForecastNoDataResponseWithoutBody() {
        KmaVillageForecastResponse response = jsonMapper.readValue("""
                {"response":{"header":{"resultCode":"03", "resultMsg":"NO_DATA"}}}
                """, KmaVillageForecastResponse.class);

        assertThat(response.response().header().resultCode()).isEqualTo("03");
        assertThat(response.response().body()).isNull();
    }
}
