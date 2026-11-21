package com.terp.erp.iam.language;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.settings.SettingsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** In-memory repositories: the service's rules on keys, fallback and the default language. */
class LanguageServiceTest {
    private final List<Language> langs = new ArrayList<>();
    private final List<LanguageLabel> rows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final LanguageLabelRepository labels = mock(LanguageLabelRepository.class);
    private final SettingsService settings = mock(SettingsService.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private final LanguageService service = new LanguageService(languages, labels, settings);

    @BeforeEach
    void setUp() {
        when(settings.system().defaultLanguage()).thenReturn("en");
        when(languages.findByCode(any())).thenAnswer(i -> langs.stream()
                .filter(l -> l.getCode().equals(i.getArgument(0))).findFirst());
        when(languages.existsByCode(any())).thenAnswer(i -> langs.stream()
                .anyMatch(l -> l.getCode().equals(i.getArgument(0))));
        when(languages.save(any())).thenAnswer(i -> {
            Language l = i.getArgument(0);
            if (l.getId() == null) {
                l.setId(ids.incrementAndGet());
                langs.add(l);
            }
            return l;
        });
        doAnswer(i -> langs.remove((Language) i.getArgument(0))).when(languages).delete(any());
        when(labels.findByLanguageId(anyLong())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getLanguageId().equals(i.getArgument(0))).toList());
        when(labels.save(any())).thenAnswer(i -> {
            rows.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(labels.saveAll(any())).thenAnswer(i -> {
            ((Iterable<LanguageLabel>) i.getArgument(0)).forEach(rows::add);
            return List.of();
        });
        doAnswer(i -> rows.remove((LanguageLabel) i.getArgument(0))).when(labels).delete(any());
        doAnswer(i -> rows.removeIf(r -> r.getLanguageId().equals(i.getArgument(0)))).when(labels).deleteByLanguage(anyLong());
        lang("en", true);
        lang("vi", true);
    }

    @Test
    void newLanguageCopiesTheDefaultAndFallsBackForMissingKeys() {
        service.importLabels("en", Map.of("menu.home", "Home", "menu.leave", "Leave"));
        service.create(new LanguageService.LanguageForm("ja", "Japanese", "JP"));
        assertThat(service.published("ja")).containsEntry("menu.home", "Home");

        service.save("ja", Map.of("menu.home", "ホーム"));
        service.importLabels("en", Map.of("menu.new", "New"));
        assertThat(service.published("ja")).containsEntry("menu.home", "ホーム").containsEntry("menu.new", "New");

        var page = service.labels("ja", "new", PageRequest.of(0, 10));
        assertThat(page.getContent()).singleElement().satisfies(r -> {
            assertThat(r.translated()).isFalse();
            assertThat(r.value()).isEqualTo("New");
        });
        // an empty text removes the own translation
        service.save("ja", Map.of("menu.home", ""));
        assertThat(service.published("ja")).containsEntry("menu.home", "Home");
    }

    @Test
    void rulesOnKeysAndLifecycle() {
        service.importLabels("en", Map.of("a.b", "x"));
        assertError(() -> service.save("vi", Map.of("unknown.key", "y")), EIamError.IAM_030);
        assertError(() -> service.importLabels("vi", Map.of("bad key!", "y")), EIamError.IAM_030);
        assertError(() -> service.create(new LanguageService.LanguageForm("vi", "Vi", null)), EIamError.IAM_026);
        assertError(() -> service.delete("vi"), EIamError.IAM_028);
        assertError(() -> service.update("en", new LanguageService.LanguageUpdateForm("English", null, false)),
                EIamError.IAM_029);
        service.create(new LanguageService.LanguageForm("ja", "Japanese", null));
        service.update("ja", new LanguageService.LanguageUpdateForm("Japanese", null, false));
        assertError(() -> service.published("ja"), EIamError.IAM_027);
        service.delete("ja");
        assertThat(langs).extracting(l -> l.getCode()).containsExactly("en", "vi");
        assertThat(rows).allMatch(r -> !r.getLanguageId().equals(103L));
    }

    private void lang(String code, boolean system) {
        Language l = new Language();
        l.setCode(code);
        l.setName(code);
        l.setEnabled(true);
        l.setSystemLang(system);
        languages.save(l);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EIamError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
