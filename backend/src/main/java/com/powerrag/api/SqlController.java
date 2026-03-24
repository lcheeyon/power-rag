package com.powerrag.api;

import com.powerrag.sql.SchemaIntrospector;
import com.powerrag.sql.SqlQueryRequest;
import com.powerrag.sql.SqlQueryResponse;
import com.powerrag.sql.TextToSqlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Text-to-SQL query endpoint.
 * POST /api/sql/query — converts natural-language to SQL, executes SELECT, returns results.
 */
@RestController
@RequestMapping("/api/sql")
@RequiredArgsConstructor
public class SqlController {

    private final TextToSqlService   textToSqlService;
    private final SchemaIntrospector schemaIntrospector;

    @PostMapping("/query")
    public ResponseEntity<SqlQueryResponse> query(
            @RequestBody @Valid SqlQueryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        String language = request.language() != null ? request.language() : "en";
        SqlQueryResponse response = textToSqlService.query(request.question(), language);
        return ResponseEntity.ok(response);
    }

    /** Admin-only: reload the schema description from the database. */
    @PostMapping("/refresh-schema")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> refreshSchema() {
        schemaIntrospector.refresh();
        return ResponseEntity.ok("Schema refreshed");
    }

    /** Returns the current schema description used in LLM prompts (admin only). */
    @GetMapping("/schema")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> schema() {
        return ResponseEntity.ok(schemaIntrospector.describe());
    }
}
