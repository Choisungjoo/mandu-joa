package com.example.helloworld.organization;

import com.example.helloworld.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership extends BaseEntity {

    public enum Role { OWNER, ADMIN, MANAGER, VIEWER }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected OrganizationMembership() {}

    public OrganizationMembership(Organization organization, AppUser user, Role role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
    }

    public Organization getOrganization() { return organization; }
    public AppUser getUser() { return user; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
