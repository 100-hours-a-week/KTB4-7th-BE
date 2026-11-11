package com.memme.service.sales.upload;

import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.cache.SharedJsonCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 매장 단위로 업로드가 바뀔 때만 달라지는 무거운 조회(적용 상품 수, 업로드별 월 목록)를 캐시한다.
 * 버전은 같은 트랜잭션 안에서 sales_uploads 행 상태와 매장의 최대 주문 ID로 만든다.
 * 주문은 업로드 처리 중에만 바뀌고, 처리가 끝나면 업로드 행의 상태·처리 시각이 바뀌므로 버전이 달라진다.
 */
@Component
public class SalesStoreDataCache {
    static final int TTL_MIN_SECONDS = 240;
    static final int TTL_MAX_SECONDS = 360;

    private final SharedJsonCache cache;
    private final SalesUploadRepository uploads;
    private final SalesOrderRepository orders;

    public SalesStoreDataCache(SharedJsonCache cache, SalesUploadRepository uploads, SalesOrderRepository orders) {
        this.cache = cache;
        this.uploads = uploads;
        this.orders = orders;
    }

    public long appliedItemCount(Long storeId, Supplier<Long> loader) {
        if (!cache.isEnabled()) return loader.get();
        String key = "sales:store:" + storeId + ":applied-items:" + version(storeId);
        var hit = cache.get(key, AppliedItems.class);
        if (hit.isPresent()) return hit.get().count();
        long count = loader.get();
        cache.put(key, new AppliedItems(count), ttl());
        return count;
    }

    public List<Object[]> uploadMonths(Long storeId, Supplier<List<Object[]>> loader) {
        if (!cache.isEnabled()) return loader.get();
        String key = "sales:store:" + storeId + ":upload-months:" + version(storeId);
        var hit = cache.get(key, UploadMonths.class);
        if (hit.isPresent()) {
            List<Object[]> rows = new ArrayList<>(hit.get().rows().size());
            for (UploadMonth row : hit.get().rows()) {
                rows.add(new Object[] {row.uploadId(), row.year(), row.month()});
            }
            return rows;
        }
        List<Object[]> rows = loader.get();
        List<UploadMonth> cached = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            cached.add(new UploadMonth(((Number) row[0]).longValue(), ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue()));
        }
        cache.put(key, new UploadMonths(cached), ttl());
        return rows;
    }

    String version(Long storeId) {
        StringBuilder source = new StringBuilder();
        source.append(orders.findMaximumIdByStoreId(storeId).orElse(0L));
        for (Object[] row : uploads.findVersionRowsByStoreId(storeId)) {
            source.append('|').append(row[0]).append(',').append(row[1]).append(',').append(row[2])
                    .append(',').append(row[3]);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static Duration ttl() {
        return Duration.ofSeconds(ThreadLocalRandom.current().nextInt(TTL_MIN_SECONDS, TTL_MAX_SECONDS + 1));
    }

    record AppliedItems(long count) {}
    record UploadMonths(List<UploadMonth> rows) {}
    record UploadMonth(long uploadId, int year, int month) {}
}
