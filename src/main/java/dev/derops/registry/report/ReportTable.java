package dev.derops.registry.report;

import java.util.List;

/**
 * Format-neutral report: one table with a title. Every report is built once as a
 * ReportTable and rendered to Excel or PDF by the same two renderers.
 * Cell values may be String, Number, LocalDate or Boolean.
 */
public record ReportTable(String title, String subtitle, List<String> headers, List<List<Object>> rows) {

    public ReportTable {
        for (List<Object> row : rows) {
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException("Row has " + row.size() + " cells, expected " + headers.size());
            }
        }
    }
}
