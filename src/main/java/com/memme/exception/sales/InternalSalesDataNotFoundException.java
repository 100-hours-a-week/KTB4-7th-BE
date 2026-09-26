package com.memme.exception.sales;

public class InternalSalesDataNotFoundException extends RuntimeException {

    public InternalSalesDataNotFoundException() {
        super("조회할 데이터가 없습니다.");
    }
}
