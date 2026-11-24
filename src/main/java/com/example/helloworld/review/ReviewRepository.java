package com.example.helloworld.review;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByLocationIdAndExternalReviewId(UUID locationId, String externalReviewId);
    Page<Review> findByLocationIdOrderByReviewCreatedAtDesc(UUID locationId, Pageable pageable);
    Page<Review> findByOrganizationIdAndReplyStatus(UUID organizationId, Review.ReplyStatus status, Pageable pageable);
}
