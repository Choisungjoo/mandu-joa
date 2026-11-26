package com.example.helloworld.persistence.sync;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncErrorRepository extends JpaRepository<SyncError, UUID> {
    List<SyncError> findBySyncRunIdOrderByOccurredAtAsc(UUID syncRunId);
}
