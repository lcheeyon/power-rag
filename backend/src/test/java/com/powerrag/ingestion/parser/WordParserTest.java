package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WordParser Unit Tests")
class WordParserTest {

    private WordParser parser;

    @BeforeEach
    void setUp() {
        parser = new WordParser();
    }

    @Test
    @DisplayName("supportedExtension returns docx")
    void supportedExtension() {
        assertThat(parser.supportedExtension()).isEqualTo("docx");
    }

    @Test
    @DisplayName("Parse Word doc with headings groups content by heading")
    void parseDocWithHeadingsGroupsByHeading() throws Exception {
        byte[] docBytes = buildWordDoc();
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(docBytes), "spec.docx");

        assertThat(sections).isNotEmpty();
    }

    @Test
    @DisplayName("Each section has doc_type WORD and file_name metadata")
    void sectionsHaveRequiredMetadata() throws Exception {
        byte[] docBytes = buildWordDoc();
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(docBytes), "arch.docx");

        for (ParsedSection s : sections) {
            assertThat(s.getMetadata().get("doc_type")).isEqualTo("WORD");
            assertThat(s.getMetadata().get("file_name")).isEqualTo("arch.docx");
            assertThat(s.getMetadata()).containsKey("heading");
            assertThat(s.getMetadata()).containsKey("section");
        }
    }

    @Test
    @DisplayName("Heading hierarchy is preserved in section metadata")
    void headingHierarchyPreserved() throws Exception {
        byte[] docBytes = buildWordDoc();
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(docBytes), "doc.docx");

        assertThat(sections.stream()
                .map(s -> (String) s.getMetadata().get("heading"))
                .filter(h -> !h.isBlank()))
                .contains("Introduction", "Architecture");
    }

    @Test
    @DisplayName("Document with no headings produces preamble section")
    void noHeadingsProducesPreamble() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("No headings here, just body text.");
            doc.write(bos);
        }
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(bos.toByteArray()), "plain.docx");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getMetadata().get("section")).isEqualTo("preamble");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private byte[] buildWordDoc() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph h1 = doc.createParagraph();
            h1.setStyle("Heading1");
            h1.createRun().setText("Introduction");

            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("This is the introduction section content.");

            XWPFParagraph h2 = doc.createParagraph();
            h2.setStyle("Heading2");
            h2.createRun().setText("Architecture");

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("Power RAG uses Qdrant for vector search.");

            doc.write(bos);
        }
        return bos.toByteArray();
    }
}
