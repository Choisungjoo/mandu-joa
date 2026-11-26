package com.example.helloworld.persistence.organization;

import com.example.helloworld.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    public enum Status { ACTIVE, DISABLED }

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {}

    public AppUser(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public @Nullable String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public @Nullable Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
