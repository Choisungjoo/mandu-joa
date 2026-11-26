package com.example.helloworld.persistence.sync;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {
    Page<SyncRun> findByPollingConfigurationIdOrderByStartedAtDesc(UUID pollingConfigurationId, Pageable pageable);
}
