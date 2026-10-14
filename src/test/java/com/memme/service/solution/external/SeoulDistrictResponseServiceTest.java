package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.exception.solution.ExternalDataLookupException;
import org.junit.jupiter.api.Test;

class SeoulDistrictResponseServiceTest {

    private final SeoulDistrictResponseService service = new SeoulDistrictResponseService();

    @Test
    void 추정매출_정상_응답을_읽는다() {
        var result = service.estimatedSales("""
                {"VwsmAdstrdSelngW":{"list_total_count":1,
                "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                "SVC_INDUTY_CD":"CS100010","THSMON_SELNG_AMT":1200000}]}}
                """);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().data().row().getFirst().salesAmount()).isEqualTo(1200000L);
    }

    @Test
    void 실제_서울시_응답처럼_지수_표기된_매출액을_읽는다() {
        var result = service.estimatedSales("""
                {"VwsmAdstrdSelngW":{"list_total_count":1,
                "RESULT":{"CODE":"INFO-000","MESSAGE":"정상"},
                "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"11110515",
                "SVC_INDUTY_CD":"CS100001","THSMON_SELNG_AMT":3.426115142E9}]}}
                """);

        assertThat(result.orElseThrow().data().row().getFirst().salesAmount()).isEqualTo(3426115142L);
    }

    @Test
    void 점포_정상_응답을_읽는다() {
        var result = service.storeCount("""
                {"VwsmAdstrdStorW":{"list_total_count":1,
                "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                "SVC_INDUTY_CD":"CS100010","SIMILR_INDUTY_STOR_CO":40}]}}
                """);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().data().row().getFirst().storeCount()).isEqualTo(40L);
    }

    @Test
    void 유동인구_정상_응답을_읽는다() {
        var result = service.footTraffic("""
                {"VwsmAdstrdFlpopW":{"list_total_count":1,
                "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                "TOT_FLPOP_CO":10000}]}}
                """);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().data().row().getFirst().totalCount()).isEqualTo(10000L);
    }

    @Test
    void 데이터_없음은_정상_빈_결과로_처리한다() {
        assertThat(service.estimatedSales("""
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """)).isEmpty();
        assertThat(service.storeCount("""
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """)).isEmpty();
        assertThat(service.footTraffic("""
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """)).isEmpty();
    }

    @Test
    void 인증_오류를_빈_결과와_구분하고_제공자_메시지를_노출하지_않는다() {
        assertThatThrownBy(() -> service.estimatedSales("""
                {"RESULT":{"CODE":"INFO-100","MESSAGE":"인증키가 유효하지 않습니다: secret"}}
                """))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_AUTH_ERROR")
                .hasMessageNotContaining("secret");
    }

    @Test
    void 기타_제공자_오류를_구분한다() {
        assertThatThrownBy(() -> service.storeCount("""
                {"RESULT":{"CODE":"ERROR-500","MESSAGE":"서버 오류"}}
                """))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_PROVIDER_ERROR");
    }

    @Test
    void 성공_코드지만_행_정보가_누락되면_잘못된_응답으로_처리한다() {
        assertThatThrownBy(() -> service.footTraffic("""
                {"VwsmAdstrdFlpopW":{"list_total_count":1,
                "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"}}}
                """))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_INVALID_RESPONSE");
    }

    @Test
    void 파싱할_수_없는_응답도_잘못된_응답으로_처리한다() {
        assertThatThrownBy(() -> service.estimatedSales("<html>error</html>"))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_INVALID_RESPONSE");
    }
}
