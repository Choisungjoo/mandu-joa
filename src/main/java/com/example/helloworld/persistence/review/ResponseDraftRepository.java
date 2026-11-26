package com.example.helloworld.persistence.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseDraftRepository extends JpaRepository<ResponseDraft, UUID> {
    List<ResponseDraft> findByReviewIdOrderByCreatedAtDesc(UUID reviewId);
    Optional<ResponseDraft> findFirstByReviewIdAndStatusIn(UUID reviewId, Collection<ResponseDraft.Status> statuses);
}
