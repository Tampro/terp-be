package com.terp.erp.recruitment.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogDtos.Option;
import com.terp.erp.recruitment.catalog.CatalogDtos.StatusCounts;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;

/** Job Locations page (docs/analysis/06 §7.1.12). Names are unique per company. */
@Service
@RequiredArgsConstructor
public class JobLocationService {
    private final JobLocationRepository locations;
    private final com.terp.erp.recruitment.posting.JobPostingRepository postings;

    public record LocationDto(String id, String name, boolean remote, String address, String city, String state,
                              String country, String postalCode, EStatus status, Instant createdDate,
                              Instant updatedDate) {
        static LocationDto of(JobLocation l) {
            return new LocationDto(l.getId().toString(), l.getName(), l.isRemote(), l.getAddress(), l.getCity(),
                    l.getState(), l.getCountry(), l.getPostalCode(), l.getStatus(), l.getCreatedDate(),
                    l.getUpdatedDate());
        }
    }

    public record LocationForm(
            @Schema(example = "Head office", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(description = "Remote work setup") boolean remote,
            @Schema(example = "1 Example Street") @Size(max = 255) String address,
            @Schema(example = "Ha Noi") @Size(max = 100) String city,
            @Schema(example = "Ha Noi") @Size(max = 100) String state,
            @Schema(example = "Vietnam") @Size(max = 100) String country,
            @Schema(example = "100000") @Size(max = 20) String postalCode,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<LocationDto> search(Long requestedCompanyId, String keyword, Boolean remote, EStatus status,
                                    Pageable pageable) {
        return locations.search(CatalogService.company(requestedCompanyId), remote, status, Texts.kw(keyword), pageable)
                .map(LocationDto::of);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Boolean remote) {
        long active = 0;
        long inactive = 0;
        for (Object[] r : locations.countByStatus(CatalogService.company(requestedCompanyId), remote, Texts.kw(keyword))) {
            if (r[0] == EStatus.ACTIVE) {
                active = (Long) r[1];
            } else {
                inactive = (Long) r[1];
            }
        }
        return new StatusCounts(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    public List<Option> options(Long requestedCompanyId) {
        return locations.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(CatalogService.company(requestedCompanyId),
                EStatus.ACTIVE).stream().map(l -> new Option(l.getId().toString(), l.getName())).toList();
    }

    @Transactional(readOnly = true)
    public LocationDto get(Long id) {
        return LocationDto.of(require(id));
    }

    @Transactional
    public LocationDto create(Long requestedCompanyId, LocationForm form) {
        JobLocation l = new JobLocation();
        l.setCompanyId(CatalogService.company(requestedCompanyId));
        apply(l, form, -1L);
        return LocationDto.of(locations.save(l));
    }

    /** Full replacement: omitted address fields are cleared, omitted status is kept. */
    @Transactional
    public LocationDto update(Long id, LocationForm form) {
        JobLocation l = require(id);
        apply(l, form, id);
        return LocationDto.of(l);
    }

    @Transactional
    public LocationDto changeStatus(Long id, EStatus status) {
        JobLocation l = require(id);
        l.setStatus(status);
        return LocationDto.of(l);
    }

    /** Refused while a job posting uses it (REC_013). */
    @Transactional
    public void delete(Long id) {
        JobLocation l = require(id);
        if (postings.existsByLocationIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        l.setDeleted(true);
    }

    private void apply(JobLocation l, LocationForm f, Long selfId) {
        String name = f.name().trim();
        if (locations.nameTaken(l.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(ERecruitmentError.REC_004);
        }
        l.setName(name);
        l.setRemote(f.remote());
        l.setAddress(Texts.blankToNull(f.address()));
        l.setCity(Texts.blankToNull(f.city()));
        l.setState(Texts.blankToNull(f.state()));
        l.setCountry(Texts.blankToNull(f.country()));
        l.setPostalCode(Texts.blankToNull(f.postalCode()));
        if (f.status() != null) {
            l.setStatus(f.status());
        }
    }

    public JobLocation require(Long id) {
        JobLocation l = locations.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_002));
        TenantGuard.assertSameCompany(l.getCompanyId(), ERecruitmentError.REC_002);
        return l;
    }
}
