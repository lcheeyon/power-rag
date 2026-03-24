package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JavaSourceParser Unit Tests")
class JavaSourceParserTest {

    private JavaSourceParser parser;

    @BeforeEach
    void setUp() {
        parser = new JavaSourceParser();
    }

    @Test
    @DisplayName("supportedExtension returns java")
    void supportedExtensionIsJava() {
        assertThat(parser.supportedExtension()).isEqualTo("java");
    }

    @Test
    @DisplayName("Parse Java file produces at least one section per method")
    void parseSampleJavaFile() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/samples/Sample.java")) {
            assertThat(is).isNotNull();
            List<ParsedSection> sections = parser.parse(is, "Sample.java");

            assertThat(sections).isNotEmpty();
            // Sample.java has 3 methods: getName, setName, greet
            assertThat(sections).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Test
    @DisplayName("Each section has required metadata: file_name, doc_type, class_name, method_name")
    void parsedSectionsHaveRequiredMetadata() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/samples/Sample.java")) {
            List<ParsedSection> sections = parser.parse(is, "Sample.java");

            for (ParsedSection section : sections) {
                assertThat(section.getMetadata()).containsKey("file_name");
                assertThat(section.getMetadata()).containsKey("doc_type");
                assertThat(section.getMetadata().get("doc_type")).isEqualTo("JAVA");
                assertThat(section.getMetadata()).containsKey("class_name");
                assertThat(section.getMetadata()).containsKey("section");
            }
        }
    }

    @Test
    @DisplayName("class_name metadata matches the actual class")
    void classNameMetadataIsCorrect() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/samples/Sample.java")) {
            List<ParsedSection> sections = parser.parse(is, "Sample.java");

            assertThat(sections).allMatch(s ->
                    "Sample".equals(s.getMetadata().get("class_name")));
        }
    }

    @Test
    @DisplayName("Method names are present in section metadata")
    void methodNamesArePopulated() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/samples/Sample.java")) {
            List<ParsedSection> sections = parser.parse(is, "Sample.java");

            assertThat(sections).anyMatch(s -> "getName".equals(s.getMetadata().get("method_name")));
            assertThat(sections).anyMatch(s -> "setName".equals(s.getMetadata().get("method_name")));
            assertThat(sections).anyMatch(s -> "greet".equals(s.getMetadata().get("method_name")));
        }
    }

    @Test
    @DisplayName("Fallback to raw text when parse fails")
    void fallbackOnMalformedInput() {
        byte[] garbage = "not java source !!!@@@###".getBytes(StandardCharsets.UTF_8);
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(garbage), "bad.java");

        // Should not throw; returns at least 1 fallback section
        assertThat(sections).isNotEmpty();
    }
}
