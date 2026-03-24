package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfParser Unit Tests")
class PdfParserTest {

    private PdfParser parser;

    @BeforeEach
    void setUp() {
        parser = new PdfParser();
    }

    @Test
    @DisplayName("supportedExtension returns pdf")
    void supportedExtension() {
        assertThat(parser.supportedExtension()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("Parse 3-page PDF returns 3 sections with page_number metadata")
    void parseThreePagePdf() throws Exception {
        byte[] pdfBytes = buildPdf(3);
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(pdfBytes), "test.pdf");

        assertThat(sections).hasSize(3);
        for (int i = 0; i < sections.size(); i++) {
            ParsedSection s = sections.get(i);
            assertThat(s.getMetadata().get("doc_type")).isEqualTo("PDF");
            assertThat(s.getMetadata().get("file_name")).isEqualTo("test.pdf");
            assertThat(s.getMetadata().get("page_number")).isEqualTo(i + 1);
            assertThat(s.getText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Every chunk has page_number metadata")
    void everyChunkHasPageNumber() throws Exception {
        byte[] pdfBytes = buildPdf(2);
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(pdfBytes), "report.pdf");

        assertThat(sections).allMatch(s -> s.getMetadata().containsKey("page_number"));
    }

    @Test
    @DisplayName("Empty PDF returns empty sections list")
    void emptyPdfReturnsEmpty() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4)); // blank page
            doc.save(bos);
        }
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(bos.toByteArray()), "blank.pdf");
        assertThat(sections).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private byte[] buildPdf(int pages) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            for (int p = 1; p <= pages; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Page " + p + " content for Power RAG test.");
                    cs.endText();
                }
            }
            doc.save(bos);
        }
        return bos.toByteArray();
    }
}
