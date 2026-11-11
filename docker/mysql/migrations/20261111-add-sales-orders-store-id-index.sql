-- 매장별 최신 주문 ID 조회(MAX(id) WHERE store_id = ?)용 인덱스.
-- 분석 캐시 버전 확인이 요청마다 실행하며, 기존 고유 키(store_id, channel, pos_order_no, ordered_at)로는
-- 매장 주문 범위를 모두 읽는다. 행을 수정하지 않는 보조 인덱스 추가이며 온라인 DDL로 적용한다.
-- 적용 전 인덱스 부재를 확인하고 Primary에서 한 번만 실행한다.
ALTER TABLE sales_orders
    ADD INDEX idx_sales_orders_store_id_id (store_id, id),
    ALGORITHM = INPLACE, LOCK = NONE;
