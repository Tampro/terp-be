package com.terp.erp.iam.colleague;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ColleagueService {
    public record ColleagueDto(String id, String username, String fullName) {
        static ColleagueDto of(UserAccount u) {
            return new ColleagueDto(String.valueOf(u.getId()), u.getUsername(), u.getFullName());
        }
    }

    private final UserAccountRepository users;

    @Transactional(readOnly = true)
    public Page<ColleagueDto> search(String keyword, Pageable pageable) {
        ErpPrincipal me = myCompanyUser();
        Page<UserAccount> page = users.search(me.companyId(), keyword == null ? "" : keyword.trim(), null, pageable);
        return new PageImpl<>(page.getContent().stream()
                .filter(u -> u.getStatus() == EUserStatus.ACTIVE && !Objects.equals(u.getId(), me.userId()))
                .map(ColleagueDto::of).toList(), pageable, page.getTotalElements());
    }

    /** 404 IAM_009 for a user of another company, a locked one or an unknown id alike. */
    @Transactional(readOnly = true)
    public ColleagueDto get(Long id) {
        ErpPrincipal me = myCompanyUser();
        return users.findByIdAndDeletedFalse(id)
                .filter(u -> Objects.equals(u.getCompanyId(), me.companyId()) && u.getStatus() == EUserStatus.ACTIVE)
                .map(ColleagueDto::of)
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_009));
    }

    private ErpPrincipal myCompanyUser() {
        ErpPrincipal me = CurrentUser.get();
        if (me.companyId() == null) {
            throw ApiException.badRequest(EIamError.IAM_003);
        }
        return me;
    }
}
