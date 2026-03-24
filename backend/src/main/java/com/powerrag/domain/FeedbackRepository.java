package com.powerrag.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findByInteractionId(UUID interactionId);
    boolean existsByInteractionIdAndUserId(UUID interactionId, UUID userId);
}
