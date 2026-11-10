package com.memme.repository.sales;

import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalesOrderBulkInsertRepository {

    static final int INSERT_BATCH_SIZE = 500;

    private static final String ORDER_INSERT_PREFIX = """
            INSERT INTO sales_orders (
                sales_upload_id, store_id, channel, pos_order_no, ordered_at, is_valid, created_at
            ) VALUES
            """;
    private static final String ORDER_ROW_PLACEHOLDERS = "(?, ?, ?, ?, ?, ?, ?)";

    private static final String ITEM_INSERT_PREFIX = """
            INSERT INTO sales_order_items (
                sales_order_id, menu_id, order_status, menu_name_raw, menu_name, menu_key, category_raw,
                standard_category, option_raw, quantity, line_price, unit_price, option_price,
                item_discount_name, item_discount_amount, order_discount_name, order_discount_amount,
                net_amount, taxable, vat_amount, item_type, created_at
            ) VALUES
            """;
    private static final String ITEM_ROW_PLACEHOLDERS =
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String ORDER_IDS_BY_UPLOAD = """
            SELECT id, store_id, channel, pos_order_no, ordered_at
            FROM sales_orders
            WHERE sales_upload_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SalesOrderBulkInsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static int insertStatementCount(int rowCount) {
        if (rowCount <= 0) {
            return 0;
        }
        return (rowCount + INSERT_BATCH_SIZE - 1) / INSERT_BATCH_SIZE;
    }

    public int insertOrders(List<SalesOrderEntity> orders) {
        LocalDateTime createdAt = LocalDateTime.now();
        return insertInBatches(orders, ORDER_INSERT_PREFIX, ORDER_ROW_PLACEHOLDERS, (statement, index, order) -> {
            statement.setLong(index++, order.getSalesUploadId());
            statement.setLong(index++, order.getStoreId());
            statement.setString(index++, order.getChannel().name());
            statement.setString(index++, order.getPosOrderNo());
            statement.setObject(index++, order.getOrderedAt());
            statement.setBoolean(index++, order.isValid());
            statement.setObject(index++, createdAtOrDefault(order.getCreatedAt(), createdAt));
            return index;
        }, "매출 주문");
    }

    public int insertItems(List<SalesOrderItemEntity> items) {
        LocalDateTime createdAt = LocalDateTime.now();
        return insertInBatches(items, ITEM_INSERT_PREFIX, ITEM_ROW_PLACEHOLDERS, (statement, index, item) -> {
            statement.setLong(index++, item.getSalesOrderId());
            statement.setObject(index++, item.getMenuId(), Types.BIGINT);
            statement.setString(index++, item.getOrderStatus().name());
            statement.setString(index++, item.getMenuNameRaw());
            statement.setString(index++, item.getMenuName());
            statement.setString(index++, item.getMenuKey());
            statement.setString(index++, item.getCategoryRaw());
            statement.setString(index++, item.getStandardCategory().name());
            statement.setString(index++, item.getOptionRaw());
            statement.setInt(index++, item.getQuantity());
            statement.setLong(index++, item.getLinePrice());
            statement.setLong(index++, item.getUnitPrice());
            statement.setLong(index++, item.getOptionPrice());
            statement.setString(index++, item.getItemDiscountName());
            statement.setLong(index++, item.getItemDiscountAmount());
            statement.setString(index++, item.getOrderDiscountName());
            statement.setLong(index++, item.getOrderDiscountAmount());
            statement.setLong(index++, item.getNetAmount());
            statement.setBoolean(index++, item.isTaxable());
            statement.setLong(index++, item.getVatAmount());
            statement.setString(index++, item.getItemType().name());
            statement.setObject(index++, createdAtOrDefault(item.getCreatedAt(), createdAt));
            return index;
        }, "매출 주문 항목");
    }

    public Map<OrderNaturalKey, Long> findOrderIdsBySalesUploadId(Long salesUploadId) {
        Objects.requireNonNull(salesUploadId, "salesUploadId");
        Map<OrderNaturalKey, Long> orderIds = new HashMap<>();
        jdbcTemplate.query(ORDER_IDS_BY_UPLOAD, resultSet -> {
            OrderNaturalKey key = new OrderNaturalKey(
                    resultSet.getLong("store_id"),
                    SalesOrderChannel.valueOf(resultSet.getString("channel")),
                    resultSet.getString("pos_order_no"),
                    resultSet.getObject("ordered_at", LocalDateTime.class)
            );
            if (orderIds.putIfAbsent(key, resultSet.getLong("id")) != null) {
                throw new IllegalStateException("같은 업로드에 자연키가 중복된 매출 주문이 있습니다.");
            }
        }, salesUploadId);
        return orderIds;
    }

    private <T> int insertInBatches(
            List<T> rows,
            String insertPrefix,
            String rowPlaceholders,
            RowBinder<T> binder,
            String rowName
    ) {
        int totalInserted = 0;
        for (int start = 0; start < rows.size(); start += INSERT_BATCH_SIZE) {
            List<T> batch = rows.subList(start, Math.min(start + INSERT_BATCH_SIZE, rows.size()));
            String sql = insertPrefix + String.join(", ", Collections.nCopies(batch.size(), rowPlaceholders));
            int inserted = jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                int index = 1;
                for (T row : batch) {
                    index = binder.bind(statement, index, row);
                }
                return statement;
            });
            if (inserted != batch.size()) {
                throw new IllegalStateException(rowName + " 벌크 저장 건수가 요청 건수와 다릅니다.");
            }
            totalInserted += inserted;
        }
        return totalInserted;
    }

    private static LocalDateTime createdAtOrDefault(LocalDateTime createdAt, LocalDateTime defaultValue) {
        return createdAt == null ? defaultValue : createdAt;
    }

    @FunctionalInterface
    private interface RowBinder<T> {
        int bind(PreparedStatement statement, int index, T row) throws SQLException;
    }

    public record OrderNaturalKey(
            Long storeId,
            SalesOrderChannel channel,
            String posOrderNo,
            LocalDateTime orderedAt
    ) {
    }
}
