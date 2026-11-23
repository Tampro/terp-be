package com.terp.erp.support.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.OwnScope;
import com.terp.erp.support.common.Texts;
import com.terp.erp.support.kb.FaqRepository;
import com.terp.erp.support.kb.KbArticleRepository;
import com.terp.erp.support.ticket.Ticket.EStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Support dashboard figures (ERP manual §5.7). Own scope (SUPPORT_TICKET_VIEW_OWN, or own=true for the staff
 * dashboard) = tickets I created or raised with my email; KB article and FAQ counts are of the whole company.
 */
@Service
@RequiredArgsConstructor
public class TicketStatsService {
    static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    private final TicketRepository tickets;
    private final TicketReplyRepository replies;
    private final TicketCategoryRepository categories;
    private final KbArticleRepository articles;
    private final FaqRepository faqs;
    private final Clock clock;

    public record CategoryCount(String categoryId, String name, long count) {
    }

    public record MonthCount(int month, long created, long closed) {
    }

    @Schema(description = "Support dashboard; scope OWN = only my tickets")
    public record TicketStats(String scope, int year, long total, long open, long closed, long today,
                              @Schema(description = "Average of (first STAFF reply - created) in minutes over the "
                                      + "tickets that have one; null when none has") BigDecimal averageFirstResponseMinutes,
                              Map<EStatus, Long> byStatus, List<CategoryCount> byCategory, long kbArticles, long faqs,
                              List<MonthCount> monthly) {
    }

    @Transactional(readOnly = true)
    public TicketStats stats(Long requestedCompanyId, Integer year, String tz, boolean ownRequested) {
        ZoneId zone = zone(tz);
        Long companyId = Texts.company(requestedCompanyId);
        boolean own = ownRequested || TicketScope.ownView();
        List<Ticket> all = own ? tickets.listOwn(companyId, OwnScope.sub(), TicketScope.myEmail())
                : tickets.findByCompanyIdAndDeletedFalse(companyId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int y = year == null ? today.getYear() : year;

        Map<EStatus, Long> byStatus = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            byStatus.put(s, 0L);
        }
        long[][] months = new long[13][2];
        long createdToday = 0;
        Map<Long, Long> perCategory = new LinkedHashMap<>();
        for (Ticket t : all) {
            byStatus.merge(t.getStatus(), 1L, Long::sum);
            perCategory.merge(t.getCategoryId(), 1L, Long::sum);
            ZonedDateTime created = at(t.getCreatedDate(), zone);
            if (created != null) {
                if (created.toLocalDate().equals(today)) {
                    createdToday++;
                }
                if (created.getYear() == y) {
                    months[created.getMonthValue()][0]++;
                }
            }
            // TODO(assumption): no closed timestamp is stored; a CLOSED ticket counts in the month of its last update.
            ZonedDateTime updated = at(t.getUpdatedDate(), zone);
            if (t.getStatus() == EStatus.CLOSED && updated != null && updated.getYear() == y) {
                months[updated.getMonthValue()][1]++;
            }
        }
        List<MonthCount> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthly.add(new MonthCount(m, months[m][0], months[m][1]));
        }

        Map<Long, String> names = new HashMap<>();
        categories.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).forEach(c -> names.put(c.getId(),
                c.getName()));
        List<CategoryCount> byCategory = new ArrayList<>();
        perCategory.forEach((id, n) -> byCategory.add(new CategoryCount(id == null ? null : id.toString(),
                id == null ? null : names.get(id), n)));

        long closed = byStatus.get(EStatus.CLOSED);
        return new TicketStats(own ? "OWN" : "ALL", y, all.size(), all.size() - closed, closed, createdToday,
                averageFirstResponse(all), byStatus, byCategory, articles.countByCompanyIdAndDeletedFalse(companyId),
                faqs.countByCompanyIdAndDeletedFalse(companyId), monthly);
    }

    private BigDecimal averageFirstResponse(List<Ticket> all) {
        if (all.isEmpty()) {
            return null;
        }
        Map<Long, Instant> created = new HashMap<>();
        all.forEach(t -> created.put(t.getId(), t.getCreatedDate()));
        long sumSeconds = 0;
        long n = 0;
        for (Object[] r : replies.firstStaffReplies(created.keySet())) {
            Instant start = created.get((Long) r[0]);
            Instant first = (Instant) r[1];
            if (start != null && first != null) {
                sumSeconds += Math.max(0, Duration.between(start, first).getSeconds());
                n++;
            }
        }
        return n == 0 ? null : BigDecimal.valueOf(sumSeconds).divide(BigDecimal.valueOf(n * 60), 1,
                RoundingMode.HALF_UP);
    }

    private static ZonedDateTime at(Instant i, ZoneId zone) {
        return Objects.isNull(i) ? null : i.atZone(zone);
    }

    // TODO(assumption): the company time zone is passed by the caller (report-service reads it from IAM settings);
    // Asia/Ho_Chi_Minh when none is given.
    static ZoneId zone(String tz) {
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? DEFAULT_ZONE : tz.trim());
        } catch (DateTimeException e) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
    }
}
