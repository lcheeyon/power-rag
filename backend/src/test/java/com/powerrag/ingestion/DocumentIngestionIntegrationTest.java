package com.powerrag.ingestion;

import com.powerrag.domain.Document;
import com.powerrag.domain.DocumentChunkRepository;
import com.powerrag.domain.DocumentRepository;
import com.powerrag.ingestion.service.DocumentIngestionService;
import com.powerrag.infrastructure.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end ingestion test: parse → chunk → embed → store in PostgreSQL.
 * VectorStore.add() is a no-op (provided by TestContainersConfig) since
 * Qdrant autoconfigure is excluded in the test profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("Document Ingestion Integration Tests")
class DocumentIngestionIntegrationTest {

    @Autowired private DocumentIngestionService ingestionService;
    @Autowired private DocumentRepository       documentRepository;
    @Autowired private DocumentChunkRepository  chunkRepository;

    @Test
    @DisplayName("Ingest Java source file → document saved as INDEXED with chunks")
    void ingestJavaFileEndToEnd() {
        String javaSource = """
                package com.example;
                public class Hello {
                    public String greet() { return "Hello RAG!"; }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "Hello.java", "text/plain",
                javaSource.getBytes(StandardCharsets.UTF_8));

        Document doc = ingestionService.ingest(file, "test java file", null);

        assertThat(doc.getStatus()).isEqualTo(Document.Status.INDEXED);
        assertThat(doc.getChunkCount()).isGreaterThan(0);
        assertThat(doc.getId()).isNotNull();

        List<?> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(doc.getId());
        assertThat(chunks).isNotEmpty();
    }

    @Test
    @DisplayName("Ingest plain-text file → document saved as INDEXED")
    void ingestTextContent() {
        String content = String.join(" ",
                java.util.Collections.nCopies(100, "Power RAG vector search test content."));
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.java", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        Document doc = ingestionService.ingest(file, "notes", null);

        assertThat(doc.getStatus()).isEqualTo(Document.Status.INDEXED);
        assertThat(doc.getChunkCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Document is persisted in PostgreSQL with correct metadata")
    void documentPersistedInPostgres() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Calc.java", "text/plain",
                "public class Calc { public int sum(int a, int b){return a+b;} }"
                        .getBytes(StandardCharsets.UTF_8));

        Document saved = ingestionService.ingest(file, "calculator class", null);

        Document found = documentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getFileName()).isEqualTo("Calc.java");
        assertThat(found.getFileType()).isEqualTo(Document.FileType.JAVA);
        assertThat(found.getStatus()).isEqualTo(Document.Status.INDEXED);
    }
}
