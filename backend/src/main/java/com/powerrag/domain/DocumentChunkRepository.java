package com.powerrag.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);
    void deleteByDocumentId(UUID documentId);

    /**
     * Full-text search on chunk_text using PostgreSQL tsvector/tsquery.
     * Results are ranked by ts_rank (BM25-like relevance).
     */
    @Query(value = """
            SELECT * FROM document_chunks
            WHERE to_tsvector('english', chunk_text) @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(to_tsvector('english', chunk_text), plainto_tsquery('english', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> fullTextSearch(@Param("query") String query, @Param("limit") int limit);
}
