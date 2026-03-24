package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PdfParser implements DocumentParser {

    @Override
    public String supportedExtension() { return "pdf"; }

    @Override
    public List<ParsedSection> parse(InputStream input, String fileName) {
        List<ParsedSection> sections = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(input.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = doc.getNumberOfPages();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc).trim();
                if (!text.isBlank()) {
                    sections.add(ParsedSection.builder()
                            .text(text)
                            .metadata(Map.of(
                                    "file_name", fileName,
                                    "doc_type", "PDF",
                                    "page_number", page,
                                    "section", "page-" + page
                            ))
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse PDF '{}': {}", fileName, e.getMessage());
        }
        return sections;
    }
}
