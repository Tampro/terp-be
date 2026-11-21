package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.model.UserAccount;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** One query for the authority resolver, which runs on every request (behind a short cache). */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findByKeycloakIdAndDeletedFalse(String keycloakId);

    Optional<UserAccount> findByIdAndDeletedFalse(Long id);

    Optional<UserAccount> findByUsernameAndDeletedFalse(String username);

    List<UserAccount> findByCompanyIdAndDeletedFalse(Long companyId);

    @Query("select distinct u from UserAccount u join u.roles r where u.companyId = :companyId and u.deleted = false"
            + " and r.code = :roleCode and r.deleted = false order by u.id")
    List<UserAccount> findByCompanyAndRole(Long companyId, String roleCode);

    boolean existsByUsernameAndDeletedFalse(String username);

    /** Forgot password: every account registered with this email (an email may serve several companies). */
    List<UserAccount> findByEmailIgnoreCaseAndDeletedFalse(String email);

    /** Accounts counting toward the plan's max_users (locked ones included: they hold a seat until deleted). */
    long countByCompanyIdAndDeletedFalse(Long companyId);

    @Query("select u from UserAccount u where u.companyId = :companyId and u.deleted = false and "
            + "(:keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%')) "
            + "or lower(u.fullName) like lower(concat('%', :keyword, '%')) "
            + "or lower(u.email) like lower(concat('%', :keyword, '%'))) "
            + "and (:roleId is null or exists (select 1 from u.roles r where r.id = :roleId))")
    Page<UserAccount> search(Long companyId, String keyword, Long roleId, Pageable pageable);

    @Query("select count(u) > 0 from UserAccount u join u.roles r where r.id = :roleId and u.deleted = false")
    boolean existsActiveUserWithRole(Long roleId);

    /** Super admin dashboard: Total Users. */
    long countByDeletedFalse();
}
