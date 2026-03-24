package com.powerrag.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for ingestion.feature BDD scenarios.
 * Uses the JWT token set by HealthCheckSteps (shared Cucumber glue scope).
 */
public class IngestionSteps {

    @LocalServerPort
    private int port;

    private Response lastResponse;

    // The JWT token is provided by HealthCheckSteps via shared scenario scope.
    // We inject it here via the shared step context (same Cucumber glue scope).
    private final HealthCheckSteps healthCheckSteps;

    public IngestionSteps(HealthCheckSteps healthCheckSteps) {
        this.healthCheckSteps = healthCheckSteps;
    }

    // ── When ───────────────────────────────────────────────────────────────

    @When("I upload a Java source file")
    public void iUploadAJavaSourceFile() {
        String source = """
                package com.example;
                public class BddTest {
                    public String hello() { return "Hello BDD!"; }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .multiPart("file", "BddTest.java",
                        source.getBytes(StandardCharsets.UTF_8), "text/plain")
                .post("/api/documents/upload");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    @When("I upload a PDF file with {int} pages")
    public void iUploadAPdfFile(int pageCount) throws Exception {
        byte[] pdfBytes = buildPdf(pageCount);
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .multiPart("file", "test.pdf", pdfBytes, "application/pdf")
                .post("/api/documents/upload");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    @When("I upload an Excel file")
    public void iUploadAnExcelFile() throws Exception {
        byte[] xlsxBytes = buildExcel();
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .multiPart("file", "data.xlsx", xlsxBytes,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .post("/api/documents/upload");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    @When("I upload a Word document")
    public void iUploadAWordDocument() throws Exception {
        byte[] docxBytes = buildDocx();
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .multiPart("file", "spec.docx", docxBytes,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .post("/api/documents/upload");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    @When("I upload a file with unsupported type {string}")
    public void iUploadUnsupportedFile(String filename) {
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .multiPart("file", filename, "fake content".getBytes(), "video/mp4")
                .post("/api/documents/upload");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    // ── Then ───────────────────────────────────────────────────────────────

    @Then("the ingestion response status should be {int}")
    public void ingestionResponseStatusShouldBe(int expected) {
        assertThat(lastResponse.statusCode()).isEqualTo(expected);
    }

    @And("the chunk count should be greater than {int}")
    public void chunkCountShouldBeGreaterThan(int min) {
        int count = lastResponse.jsonPath().getInt("chunkCount");
        assertThat(count).isGreaterThan(min);
    }

    // ── helpers ────────────────────────────────────────────────────────────

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
                    cs.showText("BDD test page " + p + " content for Power RAG ingestion.");
                    cs.endText();
                }
            }
            doc.save(bos);
        }
        return bos.toByteArray();
    }

    private byte[] buildExcel() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("TestData");
            var r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("Product");
            r0.createCell(1).setCellValue("Revenue");
            var r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Power RAG");
            r1.createCell(1).setCellValue("1000000");
            wb.write(bos);
        }
        return bos.toByteArray();
    }

    private byte[] buildDocx() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            var h1 = doc.createParagraph();
            h1.setStyle("Heading1");
            h1.createRun().setText("Overview");
            var p1 = doc.createParagraph();
            p1.createRun().setText("Power RAG BDD test document with heading hierarchy.");
            doc.write(bos);
        }
        return bos.toByteArray();
    }
}
