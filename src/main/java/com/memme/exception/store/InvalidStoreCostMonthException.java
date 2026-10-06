package com.memme.exception.store;

public class InvalidStoreCostMonthException extends RuntimeException {
    public InvalidStoreCostMonthException() {
        super("비용 기준 월은 YYYY-MM 형식으로 입력해 주세요.");
    }
}
