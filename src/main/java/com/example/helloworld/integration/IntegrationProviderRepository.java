package com.example.helloworld.integration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationProviderRepository extends JpaRepository<IntegrationProvider, String> {
}
