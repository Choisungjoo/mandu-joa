package com.example.helloworld.persistence.review;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResponsePublicationRepository extends JpaRepository<ResponsePublication, UUID> {
    Optional<ResponsePublication> findByIdempotencyKey(String idempotencyKey);

    @Query("select p from ResponsePublication p where p.status = com.example.helloworld.persistence.review.ResponsePublication.Status.QUEUED and p.nextAttemptAt <= ?1 order by p.nextAttemptAt")
    List<ResponsePublication> findDue(Instant now, Pageable pageable);
}
