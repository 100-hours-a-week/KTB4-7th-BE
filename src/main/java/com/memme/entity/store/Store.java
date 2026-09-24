package com.memme.entity.store;

import com.memme.entity.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, unique = true)
    private User owner;

    @Column(name = "business_registration_no", nullable = false, unique = true, length = 10, columnDefinition = "CHAR(10)")
    private String businessRegistrationNo;

    @Column(name = "business_verified_at", nullable = false)
    private LocalDateTime businessVerifiedAt;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "postal_code", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String postalCode;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreStatus status = StoreStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Store() {
    }

    public static Store create(
            User owner,
            String businessRegistrationNo,
            LocalDateTime businessVerifiedAt,
            String name,
            String postalCode,
            String address,
            String addressDetail,
            LocalDateTime createdAt
    ) {
        Store store = new Store();
        store.owner = owner;
        store.businessRegistrationNo = businessRegistrationNo;
        store.businessVerifiedAt = businessVerifiedAt;
        store.name = name;
        store.postalCode = postalCode;
        store.address = address;
        store.addressDetail = addressDetail;
        store.createdAt = createdAt;
        store.updatedAt = createdAt;
        return store;
    }

    public Long getId() {
        return id;
    }

    public String getStoreName() {
        return name;
    }

    public String getBusinessRegistrationNo() {
        return businessRegistrationNo;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(
            String storeName,
            String postalCode,
            String address,
            String addressDetail,
            LocalDateTime updatedAt
    ) {
        if (storeName != null) {
            this.name = storeName;
        }
        if (postalCode != null) {
            this.postalCode = postalCode;
        }
        if (address != null) {
            this.address = address;
        }
        if (addressDetail != null) {
            this.addressDetail = addressDetail;
        }
        this.updatedAt = updatedAt;
    }
}
