package com.memme.service.sales.insight;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SalesInsightRegenerationSourceFingerprint {
    private final ObjectMapper mapper;

    public SalesInsightRegenerationSourceFingerprint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String of(Object... values) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(values);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cannot fingerprint regeneration source", exception);
        }
    }
}
