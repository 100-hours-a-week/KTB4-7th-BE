package com.memme.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {
    }

    public static User create(String email, String passwordHash, String phone, LocalDateTime createdAt) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.phone = phone;
        user.createdAt = createdAt;
        user.updatedAt = createdAt;
        return user;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePassword(String passwordHash, LocalDateTime changedAt) {
        this.passwordHash = passwordHash;
        this.updatedAt = changedAt;
    }

    public void withdraw(LocalDateTime withdrawnAt) {
        deletedAt = withdrawnAt;
        updatedAt = withdrawnAt;
    }
}
