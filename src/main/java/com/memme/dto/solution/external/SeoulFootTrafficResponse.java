package com.memme.dto.solution.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulFootTrafficResponse(
        @JsonProperty("VwsmAdstrdFlpopW") Data data,
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
            @JsonProperty("TOT_FLPOP_CO") Long totalCount,
            @JsonProperty("TMZON_00_06_FLPOP_CO") Long from00To06,
            @JsonProperty("TMZON_06_11_FLPOP_CO") Long from06To11,
            @JsonProperty("TMZON_11_14_FLPOP_CO") Long from11To14,
            @JsonProperty("TMZON_14_17_FLPOP_CO") Long from14To17,
            @JsonProperty("TMZON_17_21_FLPOP_CO") Long from17To21,
            @JsonProperty("TMZON_21_24_FLPOP_CO") Long from21To24
    ) {}
}
