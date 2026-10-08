package com.memme.entity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "menu_write_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_write_store_operation_key",
                columnNames = {"store_id", "operation", "key_hash"}))
public class MenuWriteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    private MenuWriteOperation operation;

    @Column(name = "key_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String keyHash;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "json")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MenuWriteRequest() {
    }

    public static MenuWriteRequest create(Long storeId, MenuWriteOperation operation,
            String keyHash, String requestHash, String responseJson, LocalDateTime createdAt) {
        MenuWriteRequest writeRequest = new MenuWriteRequest();
        writeRequest.storeId = Objects.requireNonNull(storeId, "storeId");
        writeRequest.operation = Objects.requireNonNull(operation, "operation");
        writeRequest.keyHash = Objects.requireNonNull(keyHash, "keyHash");
        writeRequest.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        writeRequest.responseJson = Objects.requireNonNull(responseJson, "responseJson");
        writeRequest.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return writeRequest;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public MenuWriteOperation getOperation() { return operation; }
    public String getKeyHash() { return keyHash; }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
