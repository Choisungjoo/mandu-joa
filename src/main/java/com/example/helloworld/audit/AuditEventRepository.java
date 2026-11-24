package com.example.helloworld.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Insert-and-read only: no update or delete methods are exposed. */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {
    AuditEvent save(AuditEvent event);
    Page<AuditEvent> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId, Pageable pageable);
    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, UUID entityId, Pageable pageable);
    long count();
}
