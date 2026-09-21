package com.memme.service.sales;

import java.util.Locale;
import java.util.regex.Pattern;

final class TossPosTextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EMOJI = Pattern.compile("[\\p{So}\\x{FE0F}\\x{200D}]");
    private static final Pattern SQUARE_BRACKETS = Pattern.compile("\\[[^\\]]*]");
    private static final Pattern ROUND_BRACKETS = Pattern.compile("\\([^)]*\\)");

    private TossPosTextNormalizer() {
    }

    static String header(String value) {
        return collapseWhitespace(value);
    }

    static String menuName(String value) {
        return collapseWhitespace(EMOJI.matcher(value == null ? "" : value).replaceAll(""));
    }

    static String menuKey(String value) {
        String normalized = menuName(value);
        normalized = SQUARE_BRACKETS.matcher(normalized).replaceAll("");
        normalized = ROUND_BRACKETS.matcher(normalized).replaceAll("");
        return WHITESPACE.matcher(normalized).replaceAll("").toLowerCase(Locale.ROOT);
    }

    static String category(String value) {
        return menuName(value).toLowerCase(Locale.ROOT);
    }

    private static String collapseWhitespace(String value) {
        return WHITESPACE.matcher(value == null ? "" : value).replaceAll(" ").trim();
    }
}
