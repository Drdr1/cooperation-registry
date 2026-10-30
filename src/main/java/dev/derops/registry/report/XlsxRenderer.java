package dev.derops.registry.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class XlsxRenderer {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int HEADER_ROW = 3;

    public byte[] render(ReportTable table) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(table.title()));

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setWrapText(true);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Cell titleCell = sheet.createRow(0).createCell(0);
            titleCell.setCellValue(table.title());
            titleCell.setCellStyle(titleStyle);
            sheet.createRow(1).createCell(0).setCellValue(table.subtitle());

            int columns = table.headers().size();
            int[] widths = new int[columns];
            Row header = sheet.createRow(HEADER_ROW);
            for (int c = 0; c < columns; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(table.headers().get(c));
                cell.setCellStyle(headerStyle);
                widths[c] = table.headers().get(c).length();
            }

            int r = HEADER_ROW + 1;
            for (List<Object> values : table.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < columns; c++) {
                    Object v = values.get(c);
                    Cell cell = row.createCell(c);
                    if (v instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(dateStyle);
                        widths[c] = Math.max(widths[c], 10);
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        widths[c] = Math.max(widths[c], n.toString().length());
                    } else if (v != null) {
                        String s = v.toString();
                        cell.setCellValue(s);
                        widths[c] = Math.max(widths[c], s.length());
                    }
                }
            }

            // Fixed widths from content length: autoSizeColumn needs system fonts, which slim servers lack.
            for (int c = 0; c < columns; c++) {
                sheet.setColumnWidth(c, Math.min(widths[c] + 2, 60) * 256);
            }
            sheet.createFreezePane(0, HEADER_ROW + 1);
            if (!table.rows().isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, HEADER_ROW + table.rows().size(), 0, columns - 1));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
