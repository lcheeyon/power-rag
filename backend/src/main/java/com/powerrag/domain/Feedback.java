package com.powerrag.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code feedback} table (created in V1 migration).
 * Uses Short for star_rating and thumbs to match the PostgreSQL SMALLINT columns.
 */
@Entity
@Table(name = "feedback")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "interaction_id", nullable = false)
    private UUID interactionId;

    @Column(name = "user_id")
    private UUID userId;

    /** Thumbs rating: -1 (down), 0 (neutral), 1 (up). Nullable. */
    @Column(name = "thumbs")
    private Short thumbs;

    /** Star rating 1-5. Nullable. Phase 8 submits star ratings via this field. */
    @Column(name = "star_rating")
    private Short starRating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
