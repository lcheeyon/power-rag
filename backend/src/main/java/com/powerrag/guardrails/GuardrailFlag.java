package com.powerrag.guardrails;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guardrail_flags")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuardrailFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nullable — flags may be logged before the interaction row is saved. */
    @Column(name = "interaction_id")
    private UUID interactionId;

    /** INPUT or OUTPUT */
    @Column(nullable = false, length = 10)
    private String stage;

    @Column(name = "rule_triggered", nullable = false, length = 120)
    private String ruleTriggered;

    /** WARN or BLOCK */
    @Column(nullable = false, length = 10)
    private String severity;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
