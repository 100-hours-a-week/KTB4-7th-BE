package com.memme.entity.sales;

import java.time.LocalDate;

import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesEntityTest {

    @Test
    void uploadChecksumIsIndexedButNotUnique() {
        Table table = SalesUploadEntity.class.getAnnotation(Table.class);

        assertThat(table.uniqueConstraints()).isEmpty();
        assertThat(table.indexes())
                .extracting(index -> index.name() + ":" + index.columnList())
                .containsExactlyInAnyOrder(
                        "idx_sales_uploads_store_uploaded_at:store_id, uploaded_at",
                        "idx_sales_uploads_store_period:store_id, period_start, period_end",
                        "idx_sales_uploads_store_checksum:store_id, file_checksum"
                );
    }

    @Test
    void orderUsesStoreChannelPosNumberAndTimeAsNaturalKey() {
        Table table = SalesOrderEntity.class.getAnnotation(Table.class);

        assertThat(table.uniqueConstraints()).singleElement().satisfies(constraint -> {
            assertThat(constraint.name()).isEqualTo("uk_sales_orders_natural_key");
            assertThat(constraint.columnNames()).containsExactly(
                    "store_id", "channel", "pos_order_no", "ordered_at"
            );
        });
    }

    @Test
    void canceledItemPreservesNegativeQuantityAndAmounts() {
        SalesOrderItemEntity item = SalesOrderItemEntity.create(itemValues(
                SalesOrderItemStatus.CANCELED,
                -1,
                -1_000,
                1_000,
                -1_000
        ));

        assertThat(item.getQuantity()).isEqualTo(-1);
        assertThat(item.getLinePrice()).isEqualTo(-1_000);
        assertThat(item.getNetAmount()).isEqualTo(-1_000);
        assertThat(item.getVatAmount()).isEqualTo(-91);
    }

    @Test
    void completedZeroWonMenuIsValid() {
        SalesOrderItemEntity item = SalesOrderItemEntity.create(itemValues(
                SalesOrderItemStatus.COMPLETED,
                1,
                0,
                0,
                0
        ));

        assertThat(item.getQuantity()).isOne();
        assertThat(item.getNetAmount()).isZero();
    }

    @Test
    void canceledItemRejectsPositiveQuantity() {
        assertThatThrownBy(() -> SalesOrderItemEntity.create(itemValues(
                SalesOrderItemStatus.CANCELED,
                1,
                1_000,
                1_000,
                1_000
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void dailySummaryAllowsCancellationToProduceNegativeNetValues() {
        SalesDailySummaryEntity summary = SalesDailySummaryEntity.create(
                1L,
                LocalDate.of(2026, 9, 8),
                -2_000,
                -1_000,
                0,
                -1
        );

        assertThat(summary.getTotalNetAmount()).isEqualTo(-2_000);
        assertThat(summary.getMenuNetAmount()).isEqualTo(-1_000);
        assertThat(summary.getMenuQuantity()).isEqualTo(-1);
    }

    @Test
    void uploadFollowsDefinedStateTransition() {
        SalesUploadEntity upload = SalesUploadEntity.pending(
                1L,
                2L,
                "sales.xlsx",
                "sales/1.xlsx",
                "a".repeat(64)
        );

        upload.startProcessing();
        upload.setCoverage(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 840);
        upload.advancePhase(SalesUploadProcessingPhase.AGGREGATING);
        upload.complete(812);

        assertThat(upload.getStatus()).isEqualTo(SalesUploadStatus.COMPLETED);
        assertThat(upload.getValidRowCount()).isEqualTo(812L);
        assertThat(upload.getInvalidRowCount()).isEqualTo(28L);
        assertThat(upload.getProcessingPhase()).isEqualTo(SalesUploadProcessingPhase.AGGREGATING);
    }

    private SalesOrderItemEntity.Values itemValues(
            SalesOrderItemStatus status,
            int quantity,
            long linePrice,
            long unitPrice,
            long netAmount
    ) {
        return new SalesOrderItemEntity.Values(
                1L,
                null,
                status,
                "아메리카노",
                "아메리카노",
                "아메리카노",
                "커피",
                SalesStandardMenuCategory.COFFEE,
                "",
                quantity,
                linePrice,
                unitPrice,
                0,
                "",
                0,
                "",
                0,
                netAmount,
                true,
                status == SalesOrderItemStatus.CANCELED ? -91 : 0,
                SalesOrderItemType.MENU
        );
    }
}
