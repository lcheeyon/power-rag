package com.powerrag.guardrails;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuardrailFlagRepository extends JpaRepository<GuardrailFlag, UUID> {

    List<GuardrailFlag> findByInteractionId(UUID interactionId);

    List<GuardrailFlag> findByStageAndSeverity(String stage, String severity);
}
