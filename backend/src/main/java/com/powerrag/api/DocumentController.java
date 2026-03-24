package com.powerrag.api;

import com.powerrag.domain.Document;
import com.powerrag.domain.DocumentRepository;
import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import com.powerrag.ingestion.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentRepository       documentRepository;
    private final UserRepository           userRepository;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserDetails principal) {

        User user = userRepository.findByUsername(principal.getUsername()).orElse(null);
        Document doc = ingestionService.ingest(file, description, user);

        return ResponseEntity.ok(Map.of(
                "documentId",  doc.getId(),
                "fileName",    doc.getFileName(),
                "chunkCount",  doc.getChunkCount() != null ? doc.getChunkCount() : 0,
                "status",      doc.getStatus().name(),
                "uploadedAt",  doc.getCreatedAt() != null ? doc.getCreatedAt() : Instant.now()
        ));
    }

    @GetMapping
    public ResponseEntity<List<Document>> list(
            @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByUsername(principal.getUsername()).orElse(null);
        if (user == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .filter(doc -> doc.getStoragePath() != null)
                .<ResponseEntity<Resource>>map(doc -> {
                    Resource resource = new FileSystemResource(Path.of(doc.getStoragePath()));
                    if (!resource.exists()) return ResponseEntity.<Resource>notFound().build();
                    String contentType = resolveContentType(doc.getFileName());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "inline; filename=\"" + doc.getFileName() + "\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(resource);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".java")) return "text/plain";
        return "application/octet-stream";
    }
}
