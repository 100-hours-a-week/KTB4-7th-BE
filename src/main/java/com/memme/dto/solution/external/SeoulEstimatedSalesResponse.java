package com.memme.dto.solution.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulEstimatedSalesResponse(
        @JsonProperty("VwsmAdstrdSelngW") Data data,
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
            @JsonProperty("THSMON_SELNG_AMT") Long salesAmount,
            @JsonProperty("THSMON_SELNG_CO") Long salesCount,
            @JsonProperty("TMZON_00_06_SELNG_AMT") Long salesFrom00To06,
            @JsonProperty("TMZON_06_11_SELNG_AMT") Long salesFrom06To11,
            @JsonProperty("TMZON_11_14_SELNG_AMT") Long salesFrom11To14,
            @JsonProperty("TMZON_14_17_SELNG_AMT") Long salesFrom14To17,
            @JsonProperty("TMZON_17_21_SELNG_AMT") Long salesFrom17To21,
            @JsonProperty("TMZON_21_24_SELNG_AMT") Long salesFrom21To24
    ) {}
}
