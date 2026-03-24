package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PowerPointParser Unit Tests")
class PowerPointParserTest {

    private PowerPointParser parser;

    @BeforeEach
    void setUp() {
        parser = new PowerPointParser();
    }

    @Test
    @DisplayName("supportedExtension returns pptx")
    void supportedExtensionIsPptx() {
        assertThat(parser.supportedExtension()).isEqualTo("pptx");
    }

    @Test
    @DisplayName("supportedExtensions returns single-element list with pptx")
    void supportedExtensionsList() {
        assertThat(parser.supportedExtensions()).containsExactly("pptx");
    }

    @Test
    @DisplayName("Parse PPTX with two slides produces two sections")
    void parseTwoSlidesProducesTwoSections() throws Exception {
        byte[] pptxBytes = buildPptx("Title One\nBody text for slide one", "Title Two\nContent for slide two");
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(pptxBytes), "deck.pptx");

        assertThat(sections).hasSize(2);
    }

    @Test
    @DisplayName("Each section has required metadata: file_name, doc_type, page_number, section")
    void sectionsHaveRequiredMetadata() throws Exception {
        byte[] pptxBytes = buildPptx("Slide content here", "More content");
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(pptxBytes), "test.pptx");

        for (ParsedSection s : sections) {
            assertThat(s.getMetadata()).containsKey("file_name");
            assertThat(s.getMetadata()).containsKey("doc_type");
            assertThat(s.getMetadata()).containsKey("page_number");
            assertThat(s.getMetadata()).containsKey("section");
            assertThat(s.getMetadata().get("doc_type")).isEqualTo("PPTX");
            assertThat(s.getMetadata().get("file_name")).isEqualTo("test.pptx");
        }
    }

    @Test
    @DisplayName("Page numbers are assigned sequentially starting from 1")
    void pageNumbersAreSequential() throws Exception {
        byte[] pptxBytes = buildPptx("First slide", "Second slide", "Third slide");
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(pptxBytes), "seq.pptx");

        assertThat(sections).hasSize(3);
        assertThat(sections.get(0).getMetadata().get("page_number")).isEqualTo(1);
        assertThat(sections.get(1).getMetadata().get("page_number")).isEqualTo(2);
        assertThat(sections.get(2).getMetadata().get("page_number")).isEqualTo(3);
    }

    @Test
    @DisplayName("Section key follows slide-N pattern")
    void sectionKeyFollowsSlideNPattern() throws Exception {
        byte[] pptxBytes = buildPptx("Content A", "Content B");
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(pptxBytes), "named.pptx");

        assertThat(sections.get(0).getMetadata().get("section")).isEqualTo("slide-1");
        assertThat(sections.get(1).getMetadata().get("section")).isEqualTo("slide-2");
    }

    @Test
    @DisplayName("Slide text is captured in section text")
    void slideTextIsCaptured() throws Exception {
        byte[] pptxBytes = buildPptx("Hello World Slide");
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(pptxBytes), "hello.pptx");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getText()).contains("Hello World Slide");
    }

    @Test
    @DisplayName("Empty presentation returns empty list")
    void emptyPresentationReturnsEmptyList() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.write(bos);
        }
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "empty.pptx");

        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("Slides with no text are skipped")
    void slidesWithNoTextAreSkipped() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            // Add one slide with text and one blank slide (no shapes with text)
            XSLFSlide slide1 = ppt.createSlide();
            XSLFTextBox box = slide1.createTextBox();
            box.setText("Has content");
            ppt.createSlide(); // blank slide
            ppt.write(bos);
        }
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "mixed.pptx");

        assertThat(sections).hasSize(1);
    }

    @Test
    @DisplayName("Returns empty list on invalid/corrupt input")
    void returnsEmptyListOnCorruptInput() {
        byte[] garbage = "not a pptx file at all!!!".getBytes();
        List<ParsedSection> sections = parser.parse(new ByteArrayInputStream(garbage), "corrupt.pptx");

        assertThat(sections).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private byte[] buildPptx(String... slideTexts) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            for (String text : slideTexts) {
                XSLFSlide slide = ppt.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setText(text);
            }
            ppt.write(bos);
        }
        return bos.toByteArray();
    }
}
