package com.terp.erp.asset.depreciation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.depreciation.DepreciationMath.Result;
import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.AssetRepository;
import com.terp.erp.asset.item.AssetService;
import com.terp.erp.asset.type.AssetType;
import com.terp.erp.asset.type.AssetTypeRepository;
import com.terp.erp.common.ResponseListDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Depreciation tab of the Asset Details page and the Asset Depreciation Report (docs/analysis/06 §8.2.3), computed
 * at today's date from each asset (DepreciationMath); nothing is stored.
 */
@Service
@RequiredArgsConstructor
public class DepreciationService {
    private final AssetRepository assets;
    private final AssetTypeRepository types;
    private final AssetService assetService;
    private final Clock clock;

    public record TypeRef(String id, String name) {
    }

    public record Row(String assetId, String name, TypeRef assetType, String assetCode, LocalDate purchaseDate,
                      Asset.EStatus status, Result depreciation) {
    }

    /** The three cards: current value and depreciation also as a percentage of the purchase value. */
    public record Summary(long assets, BigDecimal totalPurchaseValue, BigDecimal totalCurrentValue,
                          BigDecimal currentValuePercent, BigDecimal totalDepreciation,
                          BigDecimal depreciationPercent) {
    }

    public record Report(Summary summary, ResponseListDto<Row> details) {
    }

    @Transactional(readOnly = true)
    public Result ofAsset(Long id) {
        return DepreciationMath.of(assetService.require(id), Texts.today(clock));
    }

    /** Summary over every matching asset; details paged (by name). */
    @Transactional(readOnly = true)
    public Report report(Long requestedCompanyId, Long assetTypeId, LocalDate from, LocalDate to, Pageable pageable) {
        List<Row> rows = rows(requestedCompanyId, assetTypeId, from, to);
        int start = (int) Math.min(pageable.getOffset(), rows.size());
        int end = Math.min(rows.size(), start + pageable.getPageSize());
        return new Report(summary(rows),
                ResponseListDto.of(new PageImpl<>(rows.subList(start, end), pageable, rows.size())));
    }

    /** Export CSV: UTF-8 with a BOM so Excel shows Vietnamese names. */
    @Transactional(readOnly = true)
    public byte[] csv(Long requestedCompanyId, Long assetTypeId, LocalDate from, LocalDate to) {
        StringBuilder b = new StringBuilder("﻿");
        b.append("Asset Name,Asset Type,Asset Code,Purchase Date,Purchase Cost,Depreciation Method,Current Value,"
                + "Depreciation,Depreciation %\r\n");
        for (Row r : rows(requestedCompanyId, assetTypeId, from, to)) {
            Result d = r.depreciation();
            b.append(String.join(",", cell(r.name()), cell(r.assetType() == null ? "" : r.assetType().name()),
                    cell(r.assetCode()), r.purchaseDate() == null ? "" : r.purchaseDate().toString(),
                    d.purchaseCost().toPlainString(), d.method().name(), d.currentValue().toPlainString(),
                    d.accumulatedDepreciation().toPlainString(), d.depreciatedPercent().toPlainString()))
                    .append("\r\n");
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Summary summary(List<Row> rows) {
        BigDecimal cost = rows.stream().map(r -> r.depreciation().purchaseCost()).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal current = rows.stream().map(r -> r.depreciation().currentValue()).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal dep = cost.subtract(current);
        return new Summary(rows.size(), cost, current, DepreciationMath.percent(current, cost), dep,
                DepreciationMath.percent(dep, cost));
    }

    /** Every non-deleted asset of the company matching the filters, by name. */
    public List<Row> rows(Long requestedCompanyId, Long assetTypeId, LocalDate from, LocalDate to) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Asset> list = assets.search(companyId, assetTypeId, null, from, to, null, "",
                Pageable.unpaged(Sort.by("name"))).getContent();
        Map<Long, AssetType> byType = types.findAllById(list.stream().map(Asset::getAssetTypeId).distinct().toList())
                .stream().collect(Collectors.toMap(AssetType::getId, Function.identity()));
        LocalDate today = Texts.today(clock);
        return list.stream().map(a -> {
            AssetType t = byType.get(a.getAssetTypeId());
            return new Row(a.getId().toString(), a.getName(), t == null ? null : new TypeRef(t.getId().toString(),
                    t.getName()), a.getAssetCode(), a.getPurchaseDate(), a.getStatus(), DepreciationMath.of(a, today));
        }).toList();
    }

    /** A CSV field; quotes doubled, and a leading = + - @ neutralised so a spreadsheet does not run it. */
    static String cell(String s) {
        if (s == null) {
            return "";
        }
        String v = s.isEmpty() || "=+-@".indexOf(s.charAt(0)) < 0 ? s : "'" + s;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
