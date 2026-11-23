package com.terp.erp.iam.settings;

import com.terp.erp.iam.impersonation.ImpersonationService;
import com.terp.erp.iam.language.LanguageService;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheAdminServiceTest {
    private final LocalAuthorityResolver authorities = mock(LocalAuthorityResolver.class);
    private final LanguageService languages = mock(LanguageService.class);
    private final ImpersonationService impersonation = mock(ImpersonationService.class);
    private final CacheAdminService service = new CacheAdminService(authorities, languages, impersonation);

    @Test
    void listsEachCacheWithItsSize() {
        when(authorities.estimatedSize()).thenReturn(4L);
        when(languages.estimatedCacheSize()).thenReturn(2L);

        var view = service.view();

        assertThat(view.caches()).extracting(CacheAdminService.CacheEntry::name)
                .containsExactly("authorities", "translations", "impersonation");
        assertThat(view.totalEntries()).isEqualTo(6L);
    }

    @Test
    void clearEvictsTheAuthoritiesAndEveryOtherCache() {
        service.clear();

        verify(authorities).evictAll();
        verify(languages).clearCache();
        verify(impersonation).clearCache();
    }
}
