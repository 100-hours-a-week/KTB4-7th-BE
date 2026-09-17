package com.memme.entity.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false) private Long id;
    @Column(name = "email", nullable = false, unique = true, length = 100) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "phone", nullable = false, unique = true, length = 20) private String phone;
    @Column(name = "last_login_at") private LocalDateTime lastLoginAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected User() {}
}
