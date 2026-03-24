package com.powerrag.ingestion.service;

import com.powerrag.domain.Document;
import com.powerrag.domain.DocumentChunkRepository;
import com.powerrag.domain.DocumentRepository;
import com.powerrag.ingestion.chunking.SlidingWindowChunkingStrategy;
import com.powerrag.ingestion.exception.UnsupportedDocumentTypeException;
import com.powerrag.ingestion.model.ParsedSection;
import com.powerrag.ingestion.parser.DocumentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentIngestionService Unit Tests")
class DocumentIngestionServiceTest {

    @Mock private DocumentParser        mockParser;
    @Mock private VectorStore           vectorStore;
    @Mock private DocumentRepository    documentRepository;
    @Mock private DocumentChunkRepository chunkRepository;

    private DocumentIngestionService service;
    private SlidingWindowChunkingStrategy chunkingStrategy;

    @BeforeEach
    void setUp() {
        chunkingStrategy = new SlidingWindowChunkingStrategy(512, 64);
        lenient().when(mockParser.supportedExtension()).thenReturn("pdf");
        when(mockParser.supportedExtensions()).thenReturn(List.of("pdf"));

        service = new DocumentIngestionService(
                List.of(mockParser),
                chunkingStrategy,
                vectorStore,
                documentRepository,
                chunkRepository
        );
        ReflectionTestUtils.setField(service, "storagePath", "./target/test-uploads");

        // documentRepository.save() returns the entity, assigning a UUID on first save
        lenient().when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
    }

    @Test
    @DisplayName("Correct parser is dispatched based on file extension")
    void correctParserIsDispatchedForPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf",
                "dummy content".getBytes());

        ParsedSection section = ParsedSection.builder()
                .text("some pdf text about power rag")
                .metadata(Map.of("file_name", "test.pdf", "doc_type", "PDF", "page_number", 1))
                .build();
        when(mockParser.parse(any(InputStream.class), eq("test.pdf")))
                .thenReturn(List.of(section));

        Document result = service.ingest(file, "test doc", null);

        verify(mockParser).parse(any(InputStream.class), eq("test.pdf"));
        assertThat(result.getStatus()).isIn(Document.Status.INDEXED, Document.Status.FAILED);
    }

    @Test
    @DisplayName("Chunks are stored in VectorStore and ChunkRepository")
    void chunksAreStoredInVectorStoreAndDb() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf",
                "content".getBytes());

        ParsedSection section = ParsedSection.builder()
                .text("word1 word2 word3 word4 word5")
                .metadata(Map.of("file_name", "report.pdf", "doc_type", "PDF", "page_number", 1))
                .build();
        when(mockParser.parse(any(InputStream.class), eq("report.pdf")))
                .thenReturn(List.of(section));

        service.ingest(file, null, null);

        verify(vectorStore).add(anyList());
        verify(chunkRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Document status is INDEXED on success")
    void documentStatusIsIndexedOnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.pdf", "application/pdf", "data".getBytes());

        when(mockParser.parse(any(InputStream.class), eq("ok.pdf")))
                .thenReturn(List.of(ParsedSection.builder()
                        .text("test text content")
                        .metadata(Map.of("file_name", "ok.pdf", "doc_type", "PDF", "page_number", 1))
                        .build()));

        Document result = service.ingest(file, null, null);

        assertThat(result.getStatus()).isEqualTo(Document.Status.INDEXED);
        assertThat(result.getChunkCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Document status is FAILED when parser throws exception")
    void documentStatusIsFailedWhenParserThrows() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.pdf", "application/pdf", "data".getBytes());

        when(mockParser.parse(any(InputStream.class), eq("bad.pdf")))
                .thenThrow(new RuntimeException("Simulated parse failure"));

        Document result = service.ingest(file, null, null);

        assertThat(result.getStatus()).isEqualTo(Document.Status.FAILED);
        assertThat(result.getErrorMsg()).isNotBlank();
    }

    @Test
    @DisplayName("Unsupported file type throws UnsupportedDocumentTypeException")
    void unsupportedFileTypeThrows() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "video.mp4", "video/mp4", "data".getBytes());

        assertThatThrownBy(() -> service.ingest(file, null, null))
                .isInstanceOf(UnsupportedDocumentTypeException.class)
                .hasMessageContaining("mp4");
    }

    @Test
    @DisplayName("Chunk count matches actual chunks produced")
    void chunkCountIsAccurate() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.pdf", "application/pdf", "x".getBytes());

        // 30 words → with chunk-size 512 and overlap 64 → 1 chunk
        String text = String.join(" ", java.util.Collections.nCopies(30, "word"));
        when(mockParser.parse(any(InputStream.class), eq("data.pdf")))
                .thenReturn(List.of(ParsedSection.builder()
                        .text(text)
                        .metadata(Map.of("doc_type", "PDF", "file_name", "data.pdf", "page_number", 1))
                        .build()));

        Document result = service.ingest(file, null, null);

        assertThat(result.getChunkCount()).isEqualTo(1);
    }
}
