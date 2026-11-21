package com.terp.erp.asset.item;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.item.Asset.ECondition;
import com.terp.erp.asset.item.Asset.EDepreciationMethod;
import com.terp.erp.asset.item.Asset.EStatus;
import com.terp.erp.asset.item.AssetService.AssetForm;
import com.terp.erp.asset.item.AssetService.Filter;
import com.terp.erp.asset.type.AssetType;
import com.terp.erp.asset.type.AssetTypeRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Export, import template and import of the Assets page (.xlsx). Asset Type is matched by name, Status / Condition /
 * Depreciation Method by their codes (the Import Notes of the spec). Rows with errors are reported and skipped.
 */
@Service
@RequiredArgsConstructor
public class AssetExcelService {
    static final int MAX_ROWS = 2000;
    static final String[] HEADERS = {"Name", "Asset Type", "Serial Number", "Asset Code", "Purchase Date (yyyy-MM-dd)",
            "Purchase Cost", "Status (AVAILABLE/DISPOSED)", "Condition (NEW/GOOD/FAIR/POOR/DAMAGED)", "Description",
            "Location", "Supplier", "Warranty Information", "Warranty Expiry Date (yyyy-MM-dd)",
            "Depreciation Method (STRAIGHT_LINE/DECLINING_BALANCE)", "Useful Life (Years)", "Salvage Value"};
    private static final String[] EXPORT_HEADERS = {"Name", "Asset Type", "Serial Number", "Asset Code",
            "Purchase Date", "Purchase Cost", "Status", "Condition", "Description", "Location", "Supplier",
            "Warranty Information", "Warranty Expiry Date", "Depreciation Method", "Useful Life (Years)",
            "Salvage Value"};

    private final AssetRepository assets;
    private final AssetTypeRepository types;
    private final AssetService service;
    private final RestExceptionHandler messages;

    public record RowError(int row, String code, String message) {
    }

    public record ImportResult(int rows, int created, List<RowError> errors) {
    }

    @Transactional(readOnly = true)
    public byte[] export(Long requestedCompanyId, Filter f, EStatus status) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Asset> list = assets.search(companyId, f.assetTypeId(), f.condition(), f.purchaseFrom(), f.purchaseTo(),
                status, Texts.kw(f.keyword()), Pageable.unpaged(Sort.by("name"))).getContent();
        Map<Long, String> typeNames = types.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(AssetType::getId, AssetType::getName));
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Assets");
            header(sh, EXPORT_HEADERS);
            int n = 1;
            for (Asset a : list) {
                Row x = sh.createRow(n++);
                Object[] v = {a.getName(), typeNames.getOrDefault(a.getAssetTypeId(), ""), a.getSerialNumber(),
                        a.getAssetCode(), a.getPurchaseDate(), a.getPurchaseCost(), a.getStatus(), a.getCondition(),
                        a.getDescription(), a.getLocation(), a.getSupplier(), a.getWarrantyInfo(),
                        a.getWarrantyExpiryDate(), a.getDepreciationMethod(), a.getUsefulLifeYears(),
                        a.getSalvageValue()};
                for (int i = 0; i < v.length; i++) {
                    if (v[i] instanceof BigDecimal d) {
                        x.createCell(i).setCellValue(d.doubleValue());
                    } else if (v[i] instanceof Integer k) {
                        x.createCell(i).setCellValue(k);
                    } else {
                        x.createCell(i).setCellValue(v[i] == null ? "" : v[i].toString());
                    }
                }
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("export", e);
        }
    }

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Assets");
            header(sh, HEADERS);
            String[] sample = {"Sample laptop (replace this row)", "Computer Hardware", "SN-0001", "AST-0001",
                    "2026-01-15", "25000000", "AVAILABLE", "NEW", "", "Head office", "Sample supplier", "3 years",
                    "2029-01-15", "STRAIGHT_LINE", "4", "2000000"};
            Row r = sh.createRow(1);
            for (int i = 0; i < sample.length; i++) {
                r.createCell(i).setCellValue(sample[i]);
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("template", e);
        }
    }

    /** Not one transaction: each row is saved by its own create, so a refused row cannot roll the others back. */
    public ImportResult importFile(Long requestedCompanyId, MultipartFile file) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Row> rows = rows(file);
        Map<String, AssetType> byName = types.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(t -> t.getName().toLowerCase(Locale.ROOT), Function.identity(),
                        (a, b) -> a));
        int created = 0;
        List<RowError> errors = new ArrayList<>();
        for (Row row : rows) {
            int n = row.getRowNum() + 1;
            try {
                String name = required(row, 0, n, "name");
                String typeName = required(row, 1, n, "asset type");
                AssetType type = byName.get(typeName.toLowerCase(Locale.ROOT));
                if (type == null) {
                    throw rowError(n, "asset type");
                }
                AssetForm form = new AssetForm(name, type.getId(), text(row, 2), required(row, 3, n, "asset code"),
                        date(row, 4, n, "purchase date"), money(row, 5, n, "purchase cost"),
                        option(row, 6, n, "status", EStatus.class), option(row, 7, n, "condition", ECondition.class),
                        text(row, 8), text(row, 9), text(row, 10), text(row, 11),
                        date(row, 12, n, "warranty expiry date"), null, null,
                        option(row, 13, n, "depreciation method", EDepreciationMethod.class),
                        years(row, n), money(row, 15, n, "salvage value"));
                service.create(requestedCompanyId, form);
                created++;
            } catch (ApiException ex) {
                errors.add(new RowError(n, ex.getErrorCode().getCode(),
                        messages.resolve(ex.getErrorCode(), ex.getArgs())));
            }
        }
        return new ImportResult(rows.size(), created, errors);
    }

    private static String required(Row row, int i, int n, String field) {
        String s = text(row, i);
        if (s == null) {
            throw rowError(n, field);
        }
        return s;
    }

    private static <E extends Enum<E>> E option(Row row, int i, int n, String field, Class<E> type) {
        String s = text(row, i);
        if (s == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw rowError(n, field);
        }
    }

    private static BigDecimal money(Row row, int i, int n, String field) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(c.getNumericCellValue());
            }
            String s = text(row, i);
            BigDecimal v = s == null ? null : new BigDecimal(s.replace(",", ""));
            if (v != null && v.signum() < 0) {
                throw rowError(n, field);
            }
            return v;
        } catch (NumberFormatException e) {
            throw rowError(n, field);
        }
    }

    private static Integer years(Row row, int n) {
        BigDecimal v = money(row, 14, n, "useful life");
        if (v == null) {
            return null;
        }
        if (v.compareTo(BigDecimal.valueOf(100)) > 0 || v.stripTrailingZeros().scale() > 0) {
            throw rowError(n, "useful life");
        }
        return v.intValue();
    }

    private static List<Row> rows(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sh = wb.getSheetAt(0);
            List<Row> out = new ArrayList<>();
            for (Row row : sh) {
                if (row.getRowNum() == 0 || blank(row)) {
                    continue;
                }
                out.add(row);
                if (out.size() > MAX_ROWS) {
                    throw ApiException.badRequest(EAssetError.AST_012, MAX_ROWS);
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) {
                throw a;
            }
            throw ApiException.badRequest(EAssetError.AST_011);
        }
    }

    private static boolean blank(Row row) {
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = row.getCell(i);
            if (c != null && (text(row, i) != null || isDate(c))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDate(Cell c) {
        return c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c);
    }

    private static String text(Row row, int i) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        String s = switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(c) ? null
                    : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> null;
        };
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate date(Row row, int i, int n, String field) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        try {
            if (isDate(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s = text(row, i);
            return s == null ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw rowError(n, field);
        }
    }

    private static ApiException rowError(int row, String field) {
        return ApiException.badRequest(EAssetError.AST_013, row, field);
    }

    private static void header(Sheet sh, String[] headers) {
        Row h = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            h.createCell(i).setCellValue(headers[i]);
            sh.setColumnWidth(i, 22 * 256);
        }
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
