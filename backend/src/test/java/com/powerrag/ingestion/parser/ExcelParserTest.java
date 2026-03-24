package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelParser Unit Tests")
class ExcelParserTest {

    private ExcelParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExcelParser();
    }

    @Test
    @DisplayName("supportedExtension returns xlsx")
    void supportedExtension() {
        assertThat(parser.supportedExtension()).isEqualTo("xlsx");
    }

    @Test
    @DisplayName("Parse Excel with 3 data rows returns 3 sections")
    void parseExcelWithDataRows() throws Exception {
        byte[] xlsxBytes = buildExcel(1, 3);
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(xlsxBytes), "data.xlsx");

        assertThat(sections).hasSize(3);
    }

    @Test
    @DisplayName("Each section has sheet_name and row_number metadata")
    void sectionsHaveSheetAndRowMetadata() throws Exception {
        byte[] xlsxBytes = buildExcel(1, 2);
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(xlsxBytes), "report.xlsx");

        for (ParsedSection s : sections) {
            assertThat(s.getMetadata().get("doc_type")).isEqualTo("EXCEL");
            assertThat(s.getMetadata()).containsKey("sheet_name");
            assertThat(s.getMetadata()).containsKey("row_number");
            assertThat(s.getMetadata().get("file_name")).isEqualTo("report.xlsx");
        }
    }

    @Test
    @DisplayName("Multi-sheet Excel captures sheet_name for each sheet")
    void multiSheetExcelCapturesSheetNames() throws Exception {
        byte[] xlsxBytes = buildMultiSheetExcel();
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(xlsxBytes), "multi.xlsx");

        assertThat(sections).isNotEmpty();
        assertThat(sections.stream()
                .map(s -> (String) s.getMetadata().get("sheet_name"))
                .distinct()
                .count()).isEqualTo(2); // two distinct sheet names
    }

    @Test
    @DisplayName("Blank rows are skipped")
    void blankRowsAreSkipped() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Data");
            XSSFRow r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("content");
            sheet.createRow(1); // empty row
            XSSFRow r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("more content");
            wb.write(bos);
        }
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(bos.toByteArray()), "sparse.xlsx");
        assertThat(sections).hasSize(2);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private byte[] buildExcel(int sheets, int rowsPerSheet) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            for (int s = 0; s < sheets; s++) {
                XSSFSheet sheet = wb.createSheet("Sheet" + (s + 1));
                for (int r = 0; r < rowsPerSheet; r++) {
                    XSSFRow row = sheet.createRow(r);
                    row.createCell(0).setCellValue("Col A row " + (r + 1));
                    row.createCell(1).setCellValue("Col B row " + (r + 1));
                }
            }
            wb.write(bos);
        }
        return bos.toByteArray();
    }

    private byte[] buildMultiSheetExcel() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet s1 = wb.createSheet("Sales");
            s1.createRow(0).createCell(0).setCellValue("Q1 revenue: 1000");
            XSSFSheet s2 = wb.createSheet("Costs");
            s2.createRow(0).createCell(0).setCellValue("Q1 costs: 500");
            wb.write(bos);
        }
        return bos.toByteArray();
    }
}
