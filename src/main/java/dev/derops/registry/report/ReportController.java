package dev.derops.registry.report;

import dev.derops.registry.agreement.AgreementDtos.AgreementFilter;
import dev.derops.registry.agreement.AgreementStatus;
import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    public enum Format { XLSX, PDF }

    private final ReportService reports;
    private final XlsxRenderer xlsx;
    private final PdfRenderer pdf;
    private final AuditService audit;
    private final Clock clock;

    public ReportController(ReportService reports, XlsxRenderer xlsx, PdfRenderer pdf, AuditService audit, Clock clock) {
        this.reports = reports;
        this.xlsx = xlsx;
        this.pdf = pdf;
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping("/agreements")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Transactional
    @Operation(summary = "Agreements register (Excel or PDF)", description = "Same filters as GET /api/agreements. Headers follow Accept-Language.")
    public ResponseEntity<byte[]> agreements(
            @RequestParam(defaultValue = "XLSX") Format format,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AgreementStatus status,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long responsibleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedTo) {
        AgreementFilter filter = new AgreementFilter(q, status, countryId, typeId, responsibleId, signedFrom, signedTo);
        ReportTable table = reports.agreements(filter);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("report", "agreements");
        details.put("filter", filter.toString());
        return export("agreements", format, table, details);
    }

    @GetMapping("/implementation")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Transactional
    @Operation(summary = "Implementation status of all items, with overdue flags (Excel or PDF)")
    public ResponseEntity<byte[]> implementation(@RequestParam(defaultValue = "XLSX") Format format) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("report", "implementation");
        return export("implementation", format, reports.implementation(), details);
    }

    private ResponseEntity<byte[]> export(String name, Format format, ReportTable table, Map<String, Object> details) {
        byte[] body = format == Format.PDF ? pdf.render(table) : xlsx.render(table);
        details.put("format", format.name());
        details.put("rows", table.rows().size());
        audit.record(AuditAction.EXPORT, "REPORT", null, details);

        String extension = format == Format.PDF ? "pdf" : "xlsx";
        String contentType = format == Format.PDF ? PdfRenderer.CONTENT_TYPE : XlsxRenderer.CONTENT_TYPE;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(name + "-" + LocalDate.now(clock) + "." + extension)
                .build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
