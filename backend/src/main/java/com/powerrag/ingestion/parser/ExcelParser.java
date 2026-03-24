package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExcelParser implements DocumentParser {

    @Override
    public String supportedExtension() { return "xlsx"; }

    @Override
    public List<ParsedSection> parse(InputStream input, String fileName) {
        List<ParsedSection> sections = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(input)) {
            DataFormatter formatter = new DataFormatter();
            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                String sheetName = sheet.getSheetName();
                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        String val = formatter.formatCellValue(cell).trim();
                        if (!val.isBlank()) {
                            if (!rowText.isEmpty()) rowText.append(" | ");
                            rowText.append(val);
                        }
                    }
                    if (!rowText.isEmpty()) {
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("file_name", fileName);
                        meta.put("doc_type", "EXCEL");
                        meta.put("sheet_name", sheetName);
                        meta.put("row_number", row.getRowNum() + 1);
                        meta.put("section", sheetName + "-row-" + (row.getRowNum() + 1));
                        sections.add(ParsedSection.builder()
                                .text(rowText.toString())
                                .metadata(meta)
                                .build());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse Excel '{}': {}", fileName, e.getMessage());
        }
        return sections;
    }
}
