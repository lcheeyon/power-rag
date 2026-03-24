package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses .pptx files by extracting text from each slide as a separate section.
 */
@Slf4j
@Component
public class PowerPointParser implements DocumentParser {

    @Override
    public String supportedExtension() { return "pptx"; }

    @Override
    public List<ParsedSection> parse(InputStream input, String fileName) {
        List<ParsedSection> sections = new ArrayList<>();
        try (XMLSlideShow ppt = new XMLSlideShow(input)) {
            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                int slideNum = i + 1;
                XSLFSlide slide = slides.get(i);
                StringBuilder text = new StringBuilder();

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String content = textShape.getText();
                        if (content != null && !content.isBlank()) {
                            text.append(content.trim()).append("\n");
                        }
                    }
                }

                if (!text.isEmpty()) {
                    sections.add(ParsedSection.builder()
                            .text(text.toString().trim())
                            .metadata(Map.of(
                                    "file_name",   fileName,
                                    "doc_type",    "PPTX",
                                    "page_number", slideNum,
                                    "section",     "slide-" + slideNum
                            ))
                            .build());
                }
            }
            log.info("Parsed '{}' → {} slides", fileName, sections.size());
        } catch (IOException e) {
            log.error("Failed to parse PowerPoint '{}': {}", fileName, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to parse PowerPoint '{}': {}", fileName, e.getMessage());
        }
        return sections;
    }
}
