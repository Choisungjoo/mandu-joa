package com.example.helloworld.sync;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PollingConfigurationRepository extends JpaRepository<PollingConfiguration, UUID> {

    List<PollingConfiguration> findByConnectedAccountId(UUID connectedAccountId);

    @Query("select c from PollingConfiguration c where c.enabled = true and c.nextRunAt <= ?1 "
            + "and (c.leaseExpiresAt is null or c.leaseExpiresAt < ?1) order by c.nextRunAt")
    List<PollingConfiguration> findDue(Instant now, Pageable pageable);

    /** Claims a configuration for one worker. Returns 1 when this worker won the lease. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PollingConfiguration c set c.leaseOwner = ?2, c.leaseExpiresAt = ?4 "
            + "where c.id = ?1 and (c.leaseExpiresAt is null or c.leaseExpiresAt < ?3)")
    int tryAcquireLease(UUID id, String owner, Instant now, Instant leaseUntil);
}
