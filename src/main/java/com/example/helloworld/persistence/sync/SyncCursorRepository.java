package com.example.helloworld.persistence.sync;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncCursorRepository extends JpaRepository<SyncCursor, UUID> {
    Optional<SyncCursor> findByPollingConfigurationIdAndCursorKey(UUID pollingConfigurationId, String cursorKey);
}
