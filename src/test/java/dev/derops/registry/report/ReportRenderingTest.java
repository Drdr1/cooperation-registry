package dev.derops.registry.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Both renderers must handle Cyrillic and Uzbek Latin (oʻ, gʻ) correctly. */
class ReportRenderingTest {

    private final ReportTable table = new ReportTable(
            "Реестр соглашений",
            "Shakllantirilgan sana: 2026-10-30",
            List.of("№", "Nomi", "Imzolangan sana", "Holati"),
            List.of(
                    Arrays.<Object>asList(1, "Soglashenie o sotrudnichestve", LocalDate.of(2026, 1, 15), "Действует"),
                    Arrays.<Object>asList(2, "Oʻzbekiston–Qozogʻiston yoʻl xaritasi", null, "Loyiha")));

    @Test
    void excelKeepsTypesAndUnicode() throws Exception {
        byte[] bytes = new XlsxRenderer().render(table);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Реестр соглашений");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("№");
            assertThat(sheet.getRow(4).getCell(0).getNumericCellValue()).isEqualTo(1.0);
            Cell date = sheet.getRow(4).getCell(2);
            assertThat(DateUtil.isCellDateFormatted(date)).isTrue();
            assertThat(date.getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Oʻzbekiston–Qozogʻiston yoʻl xaritasi");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    void pdfEmbedsAFontWithCyrillicGlyphs() throws Exception {
        byte[] bytes = new PdfRenderer().render(table);
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(bytes);
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Shakllantirilgan").contains("Действует");
        } finally {
            reader.close();
        }
    }

    @Test
    void rowsMustMatchTheHeaders() {
        assertThatThrownBy(() -> new ReportTable("t", "s", List.of("a", "b"), List.of(List.of("only one"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
