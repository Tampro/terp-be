package com.terp.erp.recruitment.career;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.recruitment.career.PublicCareerService.Applied;
import com.terp.erp.recruitment.career.PublicCareerService.ApplicationForm;
import com.terp.erp.recruitment.career.PublicCareerService.Filters;
import com.terp.erp.recruitment.career.PublicCareerService.JobCard;
import com.terp.erp.recruitment.career.PublicCareerService.JobDetail;
import com.terp.erp.recruitment.career.PublicCareerService.Site;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** No token (gateway and SecurityConfig permitAll, rate limited per IP at the gateway). */
@Tag(name = "Public Career Page")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/career/{slug}")
public class PublicCareerController {
    static final int MAX_PAGE = 50;
    /** Sort keys the page offers, mapped to fields; anything else falls back to the default. */
    static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Order.desc("featured"), Sort.Order.desc("publishDate")),
            "title", Sort.by("title"),
            "salary", Sort.by(Sort.Order.desc("maxSalary"), Sort.Order.desc("publishDate")),
            "deadline", Sort.by(Sort.Order.asc("applicationDeadline"), Sort.Order.desc("publishDate")));

    private final PublicCareerService service;

    @Operation(summary = "Career page header: name, headline, tagline, number of open jobs")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such enabled career page (REC_047)"))
    @GetMapping
    public ApiMessageDto<Site> site(@PathVariable String slug) {
        return ApiMessageDto.ok(service.site(slug));
    }

    @Operation(summary = "Filter Jobs panel and the source dropdown", description = "Active locations, job types "
            + "and candidate sources.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such enabled career page (REC_047)"))
    @GetMapping("/filters")
    public ApiMessageDto<Filters> filters(@PathVariable String slug) {
        return ApiMessageDto.ok(service.filters(slug));
    }

    @Operation(summary = "Open jobs (published, deadline not passed)",
            description = "keyword matches title or skill; jobTypeIds = Job Type checkboxes; minSalary / maxSalary = "
                    + "a band overlapping the job's; minPositions / maxPositions = Vacancies. sort: newest (featured "
                    + "first, default), title, salary, deadline. size at most 50.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such enabled career page (REC_047)"))
    @GetMapping("/jobs")
    public ApiMessageDto<ResponseListDto<JobCard>> jobs(
            @PathVariable String slug,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) List<Long> jobTypeIds,
            @RequestParam(required = false) BigDecimal minSalary,
            @RequestParam(required = false) BigDecimal maxSalary,
            @RequestParam(required = false) Integer minPositions,
            @RequestParam(required = false) Integer maxPositions,
            @Parameter(description = "newest, title, salary, deadline") @RequestParam(defaultValue = "newest")
            String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE),
                SORTS.getOrDefault(sort, SORTS.get("newest")));
        return ApiMessageDto.ok(ResponseListDto.of(service.jobs(slug, keyword, locationId, jobTypeIds, minSalary,
                maxSalary, minPositions, maxPositions, pageable)));
    }

    @Operation(summary = "Job details with its questions and up to 4 similar jobs")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such page (REC_047) or open job (REC_048)"))
    @GetMapping("/jobs/{id}")
    public ApiMessageDto<JobDetail> job(@PathVariable String slug, @PathVariable Long id) {
        return ApiMessageDto.ok(service.job(slug, id));
    }

    @Operation(summary = "Apply for the job",
            description = "multipart/form-data: part application (JSON), resume (PDF/DOC/DOCX ≤ 5 MB, required), "
                    + "coverLetter (optional, same rules). One application per email and job.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Resume missing (REC_050), terms not accepted (REC_049), "
                    + "applications elsewhere (REC_052), already applied (REC_016), required question (REC_017), "
                    + "other question (REC_018), file type (REC_019) or size (REC_020)"),
            @ApiResponse(responseCode = "404", description = "No such page (REC_047), open job (REC_048) or source "
                    + "(REC_001)"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this address (COMMON_006)")})
    @PostMapping(value = "/jobs/{id}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<Applied> apply(@PathVariable String slug, @PathVariable Long id,
                                        @Valid @RequestPart("application") ApplicationForm application,
                                        @RequestPart(value = "resume", required = false) MultipartFile resume,
                                        @RequestPart(value = "coverLetter", required = false)
                                        MultipartFile coverLetter) {
        return ApiMessageDto.ok(service.apply(slug, id, application, resume, coverLetter));
    }
}
