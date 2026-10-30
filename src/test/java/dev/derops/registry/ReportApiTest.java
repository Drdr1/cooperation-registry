package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class ReportApiTest extends IntegrationTest {

    @Autowired
    AuditRepository audit;

    @Test
    void agreementsRegisterAsExcelInRussian() throws Exception {
        byte[] xlsx = mvc.perform(get("/api/reports/agreements?format=XLSX")
                        .header(HttpHeaders.AUTHORIZATION, bearer("viewer"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ru"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Реестр соглашений");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Рег. номер");
            assertThat(sheet.getLastRowNum()).isEqualTo(3 + 4); // header row + 4 demo agreements
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("INT-2026-001");
            assertThat(sheet.getRow(4).getCell(8).getStringCellValue()).isEqualTo("Действует");
        }
        assertThat(audit.findByUsernameAndActionOrderByIdDesc("viewer", AuditAction.EXPORT)).isNotEmpty();
    }

    @Test
    void filtersApplyToTheReport() throws Exception {
        byte[] xlsx = mvc.perform(get("/api/reports/agreements?status=DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, bearer("viewer")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(3 + 1);
        }
    }

    @Test
    void implementationReportAsPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/api/reports/implementation?format=PDF")
                        .header(HttpHeaders.AUTHORIZATION, bearer("manager"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "uz"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5_000);
    }

    @Test
    void exportNeedsReportPermission() throws Exception {
        mvc.perform(get("/api/reports/agreements").header(HttpHeaders.AUTHORIZATION, bearer("executor1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardFigures() throws Exception {
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer("viewer"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAgreements").value(4))
                .andExpect(jsonPath("$.itemsOverdue").value(1))
                .andExpect(jsonPath("$.expiringNext90Days[0].registrationNumber").value("INT-2026-001"))
                .andExpect(jsonPath("$.agreementsByStatus[?(@.status=='IN_FORCE')].count").value(2));
    }
}
