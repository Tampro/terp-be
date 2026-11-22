package com.terp.erp.support.kb;

import com.terp.erp.exception.ApiException;
import com.terp.erp.support.common.ESupportError;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSV import of knowledge base articles and FAQs (reference KnowledgeController / FaqController import: first line =
 * header, then one item per line). UTF-8 (a BOM is dropped), comma separated, RFC 4180 quoting ("" inside quotes, line
 * breaks allowed inside quotes). The header must be exactly the expected columns, in order (case-insensitive).
 * Blank lines are skipped. Import is all-or-nothing: the caller saves only when no row has an error.
 */
public final class CsvImport {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 500;

    /** number = line of the row in the file, header = 1. */
    public record Row(int number, List<String> cells) {
        String cell(int i) {
            String s = i < cells.size() ? cells.get(i) : null;
            return s == null || s.isBlank() ? null : s.trim();
        }
    }

    public record RowError(int row, String code, String message) {
    }

    /** rows = data rows read; created = 0 whenever errors is not empty (nothing is saved). */
    public record ImportResult(int rows, int created, List<RowError> errors) {
    }

    private CsvImport() {
    }

    /** SUP_018 empty / not CSV, SUP_019 too large, SUP_033 malformed, SUP_021 wrong header, SUP_020 too many rows. */
    public static List<Row> read(MultipartFile file, List<String> header) {
        if (file == null || file.isEmpty() || !isCsv(file)) {
            throw ApiException.badRequest(ESupportError.SUP_018);
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest(ESupportError.SUP_019, MAX_BYTES / 1024);
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.badRequest(ESupportError.SUP_018);
        }
        return parse(text, header);
    }

    static List<Row> parse(String text, List<String> header) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<Row> lines = split(text);
        if (lines.isEmpty()) {
            throw ApiException.badRequest(ESupportError.SUP_018);
        }
        List<String> got = lines.get(0).cells().stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        if (!got.equals(header)) {
            throw ApiException.badRequest(ESupportError.SUP_021, String.join(",", header));
        }
        List<Row> rows = new ArrayList<>();
        for (Row r : lines.subList(1, lines.size())) {
            if (r.cells().stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(r);
            if (rows.size() > MAX_ROWS) {
                throw ApiException.badRequest(ESupportError.SUP_020, MAX_ROWS);
            }
        }
        return rows;
    }

    private static List<Row> split(String text) {
        List<Row> out = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowStart = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cur.append(c);
                }
            } else if (c == '"' && cur.length() == 0) {
                quoted = true;
            } else if (c == ',') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cur.toString());
                cur.setLength(0);
                out.add(new Row(rowStart, List.copyOf(cells)));
                cells.clear();
                line++;
                rowStart = line;
            } else {
                cur.append(c);
            }
        }
        if (quoted) {
            throw ApiException.badRequest(ESupportError.SUP_033);
        }
        if (cur.length() > 0 || !cells.isEmpty()) {
            cells.add(cur.toString());
            out.add(new Row(rowStart, List.copyOf(cells)));
        }
        return out;
    }

    private static boolean isCsv(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv");
    }
}
