package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;

import java.io.InputStream;
import java.util.List;

/**
 * Strategy interface for extracting text sections from a document.
 * Each implementation handles a specific file format.
 */
public interface DocumentParser {

    /**
     * Parse the input stream and return a list of logical sections.
     *
     * @param input    the raw file bytes
     * @param fileName original file name (used to populate file_name metadata)
     * @return ordered list of sections; never null, may be empty
     */
    List<ParsedSection> parse(InputStream input, String fileName);

    /**
     * @return the file extension this parser handles (lowercase, without dot, e.g. "pdf")
     */
    String supportedExtension();

    /** Override to support multiple extensions (default: single extension). */
    default java.util.List<String> supportedExtensions() {
        return java.util.List.of(supportedExtension());
    }
}
