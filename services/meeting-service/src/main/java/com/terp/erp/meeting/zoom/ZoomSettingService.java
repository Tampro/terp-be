package com.terp.erp.meeting.zoom;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.meeting.zoom.ZoomClient.Credentials;

import java.time.Clock;
import java.time.Instant;

/**
 * Settings > Zoom Settings (docs/analysis/06 §9.3 prerequisites; PO 2026-11-21). The client secret is encrypted on
 * save and never returned; a blank secret on save keeps the stored one. Saving resets "verified"; Test Connection sets
 * it, which turns the Zoom Meeting menu on (status).
 */
@Service
@RequiredArgsConstructor
public class ZoomSettingService {
    private final ZoomSettingRepository settings;
    private final SecretCipher cipher;
    private final ZoomClient zoom;
    private final Clock clock;

    public record SettingsForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String accountId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String clientId,
            @Schema(description = "Write only; blank = keep the stored secret (required the first time)")
            @Size(max = 200) String clientSecret) {
    }

    /** clientSecretSet instead of the secret itself. */
    public record SettingsDto(String accountId, String clientId, boolean clientSecretSet, boolean verified,
                              Instant verifiedAt) {
    }

    /** enabled = the Zoom Meeting menu is shown. */
    public record Status(boolean configured, boolean enabled) {
    }

    @Transactional(readOnly = true)
    public SettingsDto get(Long requestedCompanyId) {
        return settings.findByCompanyId(Texts.company(requestedCompanyId)).map(ZoomSettingService::dto)
                .orElse(new SettingsDto(null, null, false, false, null));
    }

    @Transactional(readOnly = true)
    public Status status(Long requestedCompanyId) {
        return settings.findByCompanyId(Texts.company(requestedCompanyId))
                .map(s -> new Status(true, s.isVerified())).orElse(new Status(false, false));
    }

    @Transactional
    public SettingsDto save(Long requestedCompanyId, SettingsForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        ZoomSetting s = settings.findByCompanyId(companyId).orElseGet(() -> {
            ZoomSetting n = new ZoomSetting();
            n.setCompanyId(companyId);
            return n;
        });
        String secret = Texts.blankToNull(f.clientSecret());
        if (secret == null && s.getClientSecretCipher() == null) {
            throw ApiException.badRequest(EMeetingError.MTG_025);
        }
        s.setAccountId(f.accountId().trim());
        s.setClientId(f.clientId().trim());
        if (secret != null) {
            s.setClientSecretCipher(cipher.encrypt(secret, companyId));
        }
        s.setVerified(false);
        s.setVerifiedAt(null);
        return dto(settings.save(s));
    }

    /** Test Connection with the stored credentials; the result is remembered either way. */
    @Transactional(noRollbackFor = ApiException.class)
    public SettingsDto test(Long requestedCompanyId) {
        ZoomSetting s = settings.findByCompanyId(Texts.company(requestedCompanyId))
                .orElseThrow(() -> ApiException.badRequest(EMeetingError.MTG_022));
        try {
            zoom.test(credentials(s));
            s.setVerified(true);
            s.setVerifiedAt(clock.instant());
        } catch (ApiException e) {
            if (e.getErrorCode() == EMeetingError.MTG_023) {
                s.setVerified(false);
                s.setVerifiedAt(null);
            }
            throw e;
        }
        return dto(s);
    }

    /** The credentials of a company whose connection was tested (MTG_022 otherwise), for the meetings. */
    public Credentials verified(Long companyId) {
        ZoomSetting s = settings.findByCompanyId(companyId).filter(ZoomSetting::isVerified)
                .orElseThrow(() -> ApiException.badRequest(EMeetingError.MTG_022));
        return credentials(s);
    }

    private Credentials credentials(ZoomSetting s) {
        return new Credentials(s.getCompanyId(), s.getAccountId(), s.getClientId(),
                cipher.decrypt(s.getClientSecretCipher(), s.getCompanyId()));
    }

    private static SettingsDto dto(ZoomSetting s) {
        return new SettingsDto(s.getAccountId(), s.getClientId(), s.getClientSecretCipher() != null, s.isVerified(),
                s.getVerifiedAt());
    }
}
