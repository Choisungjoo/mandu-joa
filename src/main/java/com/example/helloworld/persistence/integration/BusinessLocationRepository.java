package com.example.helloworld.persistence.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessLocationRepository extends JpaRepository<BusinessLocation, UUID> {
    List<BusinessLocation> findByOrganizationId(UUID organizationId);
    List<BusinessLocation> findByConnectedAccountId(UUID connectedAccountId);
    Optional<BusinessLocation> findByOrganizationIdAndProviderCodeAndExternalLocationId(
            UUID organizationId, String providerCode, String externalLocationId);
}
