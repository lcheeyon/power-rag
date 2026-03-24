package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WordParser implements DocumentParser {

    @Override
    public String supportedExtension() { return "docx"; }

    @Override
    public List<ParsedSection> parse(InputStream input, String fileName) {
        List<ParsedSection> sections = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(input)) {
            String currentHeading = "";
            StringBuilder sectionText = new StringBuilder();

            for (XWPFParagraph para : doc.getParagraphs()) {
                String style = para.getStyle();
                String text  = para.getText().trim();
                if (text.isBlank()) continue;

                boolean isHeading = style != null && style.toLowerCase().startsWith("heading");
                if (isHeading) {
                    // flush accumulated section
                    if (!sectionText.isEmpty()) {
                        sections.add(buildSection(fileName, currentHeading, sectionText.toString()));
                        sectionText.setLength(0);
                    }
                    currentHeading = text;
                } else {
                    sectionText.append(text).append("\n");
                }
            }
            // flush last section
            if (!sectionText.isEmpty()) {
                sections.add(buildSection(fileName, currentHeading, sectionText.toString()));
            }
        } catch (IOException e) {
            log.error("Failed to parse Word doc '{}': {}", fileName, e.getMessage());
        }
        return sections;
    }

    private ParsedSection buildSection(String fileName, String heading, String text) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("file_name", fileName);
        meta.put("doc_type", "WORD");
        meta.put("heading", heading);
        meta.put("section", heading.isBlank() ? "preamble" : heading);
        return ParsedSection.builder().text(text.trim()).metadata(meta).build();
    }
}
