package com.example.helloworld.persistence.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    List<OrganizationMembership> findByUserId(UUID userId);
    List<OrganizationMembership> findByOrganizationId(UUID organizationId);
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
