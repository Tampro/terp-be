package com.terp.erp.iam.settings;

import com.terp.erp.iam.impersonation.ImpersonationService;
import com.terp.erp.iam.language.LanguageService;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Settings → Cache (ERP manual 3.7 "size, Clear Cache, Optimize") for IAM's in-memory Caffeine caches. Every cache
 * listed here only saves a database read, so clearing it is always safe. Deliberately not listed or cleared: the
 * revoked-session store (clearing it would let logged-out tokens work again) and the login rate limiter (clearing it
 * would reset brute-force counters). Hit rates are not reported: the caches do not record stats (it costs on every
 * request). "Optimize" (Laravel's artisan optimize) has no JVM equivalent and is not offered. TODO: other services'
 * caches (RemoteAuthorityResolver 60 s, media storage settings) are out of reach here; they expire within a minute.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheAdminService {
    public record CacheEntry(String name, String description, long estimatedSize, String expiresAfter) {
    }

    public record CacheView(List<CacheEntry> caches, long totalEntries) {
    }

    private final LocalAuthorityResolver authorities;
    private final LanguageService languages;
    private final ImpersonationService impersonation;

    private record Managed(String name, String description, String expiresAfter, LongSupplier size,
                           Runnable clear) {
    }

    private List<Managed> managed() {
        return List.of(
                new Managed("authorities", "Roles and permissions of signed-in users", "30s",
                        authorities::estimatedSize, authorities::evictAll),
                new Managed("translations", "Published language labels", "10m",
                        languages::estimatedCacheSize, languages::clearCache),
                new Managed("impersonation", "Login as Company sessions", "15s",
                        impersonation::estimatedCacheSize, impersonation::clearCache));
    }

    public CacheView view() {
        List<CacheEntry> entries = managed().stream()
                .map(m -> new CacheEntry(m.name(), m.description(), m.size().getAsLong(), m.expiresAfter())).toList();
        return new CacheView(entries, entries.stream().mapToLong(CacheEntry::estimatedSize).sum());
    }

    /** Clear Cache: every listed cache; the next requests read from the database. */
    public CacheView clear() {
        managed().forEach(m -> m.clear().run());
        log.info("IAM caches cleared by a super admin");
        return view();
    }
}
