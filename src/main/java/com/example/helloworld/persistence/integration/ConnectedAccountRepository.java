package com.example.helloworld.persistence.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, UUID> {
    List<ConnectedAccount> findByOrganizationId(UUID organizationId);
    Optional<ConnectedAccount> findByOrganizationIdAndProviderCodeAndExternalAccountId(
            UUID organizationId, String providerCode, String externalAccountId);
}
