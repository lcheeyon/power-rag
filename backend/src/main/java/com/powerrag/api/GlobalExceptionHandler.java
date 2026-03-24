package com.powerrag.api;

import com.powerrag.feedback.DuplicateFeedbackException;
import com.powerrag.ingestion.exception.UnsupportedDocumentTypeException;
import com.powerrag.sql.exception.SqlValidationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates domain exceptions to HTTP responses without forwarding to /error,
 * which would strip the Authorization header and return 401.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedDocumentType(
            UnsupportedDocumentTypeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SqlValidationException.class)
    public ResponseEntity<Map<String, String>> handleSqlValidation(SqlValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateFeedbackException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateFeedback(DuplicateFeedbackException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
