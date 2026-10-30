package dev.derops.registry.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link ReportTable} as an A4 landscape PDF. Uses an embedded DejaVu
 * Sans font: the standard PDF fonts have no Cyrillic glyphs, and Uzbek needs ʻ.
 */
@Component
public class PdfRenderer {

    public static final String CONTENT_TYPE = "application/pdf";

    private static final Color HEADER_BG = new Color(226, 230, 236);

    private final BaseFont regular;
    private final BaseFont bold;

    public PdfRenderer() {
        this.regular = load("fonts/DejaVuSans.ttf");
        this.bold = load("fonts/DejaVuSans-Bold.ttf");
    }

    public byte[] render(ReportTable table) {
        Font titleFont = new Font(bold, 14);
        Font subtitleFont = new Font(regular, 9, Font.NORMAL, Color.DARK_GRAY);
        Font headFont = new Font(bold, 8.5f);
        Font bodyFont = new Font(regular, 8.5f);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle(table.title());
            HeaderFooter footer = new HeaderFooter(new Phrase("", subtitleFont), true);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setBorder(Rectangle.NO_BORDER);
            doc.setFooter(footer);
            doc.open();

            doc.add(new Paragraph(table.title(), titleFont));
            Paragraph subtitle = new Paragraph(table.subtitle(), subtitleFont);
            subtitle.setSpacingAfter(10);
            doc.add(subtitle);

            PdfPTable pdfTable = new PdfPTable(table.headers().size());
            pdfTable.setWidthPercentage(100);
            pdfTable.setHeaderRows(1);
            for (String h : table.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(4);
                pdfTable.addCell(cell);
            }
            for (List<Object> row : table.rows()) {
                for (Object v : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(format(v), bodyFont));
                    cell.setPadding(3);
                    pdfTable.addCell(cell);
                }
            }
            doc.add(pdfTable);
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof LocalDate d) {
            return d.toString();
        }
        return v.toString();
    }

    private static BaseFont load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String name = path.substring(path.lastIndexOf('/') + 1);
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load font " + path, e);
        }
    }
}
