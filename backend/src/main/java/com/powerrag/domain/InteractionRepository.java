package com.powerrag.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {
    List<Interaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<Interaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
