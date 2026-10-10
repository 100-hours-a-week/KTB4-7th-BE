package com.memme.exception.ranking;

public class UnsupportedRankingConditionException extends RuntimeException {

    public UnsupportedRankingConditionException() {
        super("지원하지 않는 조회 조건입니다.");
    }
}
