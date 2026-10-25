package com.memme.service.sales.outbox;

public class SalesOutboxOwnershipLostException extends RuntimeException {
    public SalesOutboxOwnershipLostException() { super("Outbox 실행 소유권이 유효하지 않습니다."); }
}
