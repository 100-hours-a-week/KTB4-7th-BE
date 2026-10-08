package com.memme.dto.store;

import java.math.BigInteger;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictMenuPriceDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            parser.skipChildren();
            return null;
        }
        try {
            return new BigInteger(parser.getText()).longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
