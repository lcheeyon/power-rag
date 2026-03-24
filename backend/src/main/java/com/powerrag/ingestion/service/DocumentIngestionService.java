package com.powerrag.ingestion.service;

import com.powerrag.domain.Document;
import com.powerrag.domain.DocumentChunk;
import com.powerrag.domain.DocumentChunkRepository;
import com.powerrag.domain.DocumentRepository;
import com.powerrag.domain.User;
import com.powerrag.ingestion.chunking.ChunkingStrategy;
import com.powerrag.ingestion.exception.UnsupportedDocumentTypeException;
import com.powerrag.ingestion.model.Chunk;
import com.powerrag.ingestion.model.ParsedSection;
import com.powerrag.ingestion.parser.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document.Builder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final List<DocumentParser>      parsers;
    private final ChunkingStrategy          chunkingStrategy;
    private final VectorStore               vectorStore;
    private final DocumentRepository        documentRepository;
    private final DocumentChunkRepository   chunkRepository;

    @Value("${powerrag.upload.storage-path:./uploads}")
    private String storagePath;

    /**
     * Ingest a multipart file upload: parse → chunk → embed → store in Qdrant + PostgreSQL.
     *
     * @param file the uploaded file
     * @param description optional human-readable description
     * @param user the authenticated user performing the upload
     * @return the saved Document entity (status INDEXED or FAILED)
     */
    @Transactional
    public Document ingest(MultipartFile file, String description, User user) {
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        String ext = extension(fileName);

        DocumentParser parser = parsers.stream()
                .filter(p -> p.supportedExtensions().stream()
                        .anyMatch(e -> e.equalsIgnoreCase(ext)))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentTypeException(
                        "Unsupported file type: ." + ext));

        Document doc = documentRepository.save(Document.builder()
                .fileName(fileName)
                .fileType(resolveFileType(ext))
                .fileSize(file.getSize())
                .description(description)
                .status(Document.Status.PENDING)
                .user(user)
                .build());

        try {
            // Save original file to disk for citation download
            Path dir = Paths.get(storagePath).resolve(doc.getId().toString());
            Files.createDirectories(dir);
            Path stored = dir.resolve(fileName);
            Files.write(stored, file.getBytes());
            doc.setStoragePath(stored.toString());

            List<ParsedSection> sections = parser.parse(file.getInputStream(), fileName);
            List<Chunk>         chunks   = chunkingStrategy.chunk(sections);

            List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();
            List<DocumentChunk> chunkEntities = new ArrayList<>();

            for (Chunk chunk : chunks) {
                String qdrantId = UUID.randomUUID().toString();
                Map<String, Object> metadata = new java.util.HashMap<>(chunk.getMetadata());
                metadata.put("document_id", doc.getId().toString());

                aiDocs.add(new Builder()
                        .id(qdrantId)
                        .text(chunk.getText())
                        .metadata(metadata)
                        .build());

                chunkEntities.add(DocumentChunk.builder()
                        .document(doc)
                        .qdrantId(qdrantId)
                        .chunkIndex(chunk.getIndex())
                        .chunkText(chunk.getText())
                        .metadata(metadata)
                        .build());
            }

            vectorStore.add(aiDocs);
            chunkRepository.saveAll(chunkEntities);

            doc.setChunkCount(chunks.size());
            doc.setStatus(Document.Status.INDEXED);
            log.info("Ingested '{}' → {} chunks", fileName, chunks.size());

        } catch (IOException e) {
            log.error("IO error ingesting '{}': {}", fileName, e.getMessage());
            doc.setStatus(Document.Status.FAILED);
            doc.setErrorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("Error ingesting '{}': {}", fileName, e.getMessage());
            doc.setStatus(Document.Status.FAILED);
            doc.setErrorMsg(e.getMessage());
        }

        return documentRepository.save(doc);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private Document.FileType resolveFileType(String ext) {
        return switch (ext.toLowerCase()) {
            case "java"                    -> Document.FileType.JAVA;
            case "pdf"                     -> Document.FileType.PDF;
            case "xlsx"                    -> Document.FileType.EXCEL;
            case "docx"                    -> Document.FileType.WORD;
            case "pptx"                    -> Document.FileType.PPTX;
            case "png","jpg","jpeg","gif","webp" -> Document.FileType.IMAGE;
            default -> throw new UnsupportedDocumentTypeException("Unknown type: " + ext);
        };
    }
}
