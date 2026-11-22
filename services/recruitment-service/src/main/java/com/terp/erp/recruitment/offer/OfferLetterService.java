package com.terp.erp.recruitment.offer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.career.CareerSettings;
import com.terp.erp.recruitment.career.CareerSettingsRepository;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.Generated;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Download Offer Letter: an offer template of the offer's company filled from the offer, as a PDF. Values are
 * HTML-escaped by the template fill and the PDF takes text only. Email sending is out of scope (no notification
 * service yet). TODO: email the letter once the notification service exists.
 */
@Service
@RequiredArgsConstructor
public class OfferLetterService {
    /** Variables a template may declare to get offer data (names of the TERP Core offer email templates). */
    public static final Set<String> VARIABLES = Set.of("candidate_name", "candidate_email", "position", "job_title",
            "job_code", "department", "salary", "start_date", "offer_expiration_date", "expiration_date", "offer_date",
            "benefits", "company_name");

    private final OfferService offerService;
    private final OfferTemplateService templates;
    private final CandidateRepository candidates;
    private final JobPostingRepository postings;
    private final CareerSettingsRepository careerSettings;
    private final OrgClient org;

    public record Letter(String fileName, byte[] pdf) {
    }

    @Transactional(readOnly = true)
    public Letter letter(Long offerId, Long templateId) {
        Offer o = offerService.require(offerId);
        Generated g = templates.letter(templateId, o.getCompanyId(), values(o));
        return new Letter("offer-letter-" + o.getId() + ".pdf", OfferPdf.render(g.name(), g.content()));
    }

    Map<String, String> values(Offer o) {
        Candidate c = candidates.findById(o.getCandidateId()).orElse(null);
        JobPosting p = postings.findById(o.getJobPostingId()).orElse(null);
        Map<String, String> v = new HashMap<>();
        v.put("candidate_name", c == null ? "" : c.fullName());
        v.put("candidate_email", c == null ? "" : c.getEmail());
        v.put("position", p == null ? "" : p.getTitle());
        v.put("job_title", p == null ? "" : p.getTitle());
        v.put("job_code", p == null ? "" : p.getJobCode());
        String department = o.getDepartmentId() == null ? null : org.departmentName(o.getDepartmentId());
        v.put("department", department == null ? "" : department);
        v.put("salary", money(o.getSalary()));
        v.put("start_date", date(o.getStartDate()));
        v.put("offer_expiration_date", date(o.getExpirationDate()));
        v.put("expiration_date", date(o.getExpirationDate()));
        v.put("offer_date", date(o.getOfferDate()));
        v.put("benefits", o.getBenefits() == null ? "" : o.getBenefits());
        // TODO(assumption): the company name is the career page display name (recruitment has no company record)
        v.put("company_name", careerSettings.findByCompanyId(o.getCompanyId()).map(CareerSettings::getDisplayName)
                .orElse(""));
        return v;
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))
                .format(amount);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.toString();
    }
}
