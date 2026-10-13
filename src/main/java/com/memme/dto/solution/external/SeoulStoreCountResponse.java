package com.memme.dto.solution.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulStoreCountResponse(
        @JsonProperty("VwsmAdstrdStorW") Data data,
        @JsonProperty("RESULT") Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("list_total_count") Integer totalCount,
            @JsonProperty("RESULT") Result result,
            List<Row> row
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("CODE") String code, @JsonProperty("MESSAGE") String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("STDR_YYQU_CD") String quarterCode,
            @JsonProperty("ADSTRD_CD") String administrativeDongCode,
            @JsonProperty("ADSTRD_CD_NM") String administrativeDongName,
            @JsonProperty("SVC_INDUTY_CD") String industryCode,
            @JsonProperty("SVC_INDUTY_CD_NM") String industryName,
            @JsonProperty("SIMILR_INDUTY_STOR_CO") Long storeCount,
            @JsonProperty("OPBIZ_STOR_CO") Long openCount,
            @JsonProperty("CLSBIZ_STOR_CO") Long closeCount
    ) {}
}
