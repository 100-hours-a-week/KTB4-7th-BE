package com.memme.dto.sales;

import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictCostNumberDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            return context.reportInputMismatch(BigDecimal.class, "비용은 숫자로 입력해 주세요.");
        }
        return parser.getDecimalValue();
    }
}
