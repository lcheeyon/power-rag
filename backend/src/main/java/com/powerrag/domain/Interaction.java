package com.powerrag.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Interaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String queryLanguage = "en";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseText;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String responseLanguage = "en";

    @Column(nullable = false, length = 20)
    private String modelProvider;

    @Column(nullable = false, length = 80)
    private String modelId;

    @Column
    private Double confidence;

    @Column(nullable = false)
    @Builder.Default
    private boolean cacheHit = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> sources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> topChunkIds;

    /** MCP tool calls during the LLM turn (null if none). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> mcpInvocations;

    @Column(nullable = false)
    @Builder.Default
    private boolean guardrailFlag = false;

    @Column
    private String flagReason;

    @Column
    private Integer durationMs;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
