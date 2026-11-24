package com.dcpost.customer.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false, unique = true, length = 20)
    private String phone;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(length = 500)
    private String address;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;
    @Column(name = "refresh_expires_at")
    private Instant refreshExpiresAt;
    @Column(name = "auth_version", nullable = false)
    private long authVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {}

    public User(String fullName, String email, String phone, String passwordHash, Instant now) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void assignRole(Role role) { roles.add(role); }

    public void startSession(String refreshHash, Instant expiresAt, Instant now) {
        authVersion++;
        rotateRefresh(refreshHash, expiresAt, now);
    }

    public void rotateRefresh(String refreshHash, Instant expiresAt, Instant now) {
        refreshTokenHash = refreshHash;
        refreshExpiresAt = expiresAt;
        updatedAt = now;
    }

    public void revokeSession(Instant now) {
        authVersion++;
        refreshTokenHash = null;
        refreshExpiresAt = null;
        updatedAt = now;
    }

    public void updateProfile(String fullName, String phone, String address, Instant now) {
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        revokeSession(now);
    }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
    public long getAuthVersion() { return authVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
