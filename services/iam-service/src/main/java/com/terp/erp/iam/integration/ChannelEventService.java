package com.terp.erp.iam.integration;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.integration.CompanyChannelEvent.EChatChannel;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-event switches of the chat channels (ERP manual 19 integrations): which routed events Slack, Telegram and
 * SMS fire for. TODO(assumption): a company that never saved the list gets every event on (today's behaviour), and
 * an event added to the catalog after a save is on until the list is saved again — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class ChannelEventService {
    public record EventSwitch(String eventType, String name, boolean enabled) {
    }

    public record ModuleSwitches(EChannelEvent.EModule module, List<EventSwitch> events) {
    }

    /** configured: the company saved the list once (otherwise every event is on by default). */
    public record ChannelEventsView(EChatChannel channel, boolean configured, List<ModuleSwitches> modules) {
    }

    public record ChannelEventsForm(
            @Schema(description = "Full replacement: the event types the channel fires for; every other catalog "
                    + "event is switched off. Empty = none.", example = "[\"sales.invoice.posted\",\"crm.lead.created\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Size(max = 200) List<@NotNull @Size(max = 100) String> enabledEventTypes) {
    }

    private final CompanyChannelEventRepository repo;

    @Transactional(readOnly = true)
    public ChannelEventsView get(Long requestedCompanyId, String channelPath) {
        EChatChannel ch = channel(channelPath);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return view(cid, ch);
    }

    /** Full replacement of the enabled list; one row per catalog event is kept, updated in place. */
    @Transactional
    public ChannelEventsView replace(Long requestedCompanyId, String channelPath, ChannelEventsForm form) {
        EChatChannel ch = channel(channelPath);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        Set<String> enabled = new HashSet<>();
        for (String t : form.enabledEventTypes()) {
            if (EChannelEvent.ofEventType(t).isEmpty()) {
                throw ApiException.badRequest(ESettingsError.SETTINGS_021, t);
            }
            enabled.add(t);
        }
        Map<String, CompanyChannelEvent> rows = repo.findByCompanyIdAndChannel(cid, ch).stream()
                .collect(Collectors.toMap(CompanyChannelEvent::getEventType, Function.identity(), (a, b) -> a));
        for (EChannelEvent e : EChannelEvent.values()) {
            CompanyChannelEvent row = rows.get(e.getEventType());
            if (row == null) {
                row = new CompanyChannelEvent();
                row.setCompanyId(cid);
                row.setChannel(ch);
                row.setEventType(e.getEventType());
            }
            row.setEnabled(enabled.contains(e.getEventType()));
            repo.save(row);
        }
        return view(cid, ch);
    }

    /** For the internal config: no row = on; an event outside the catalog is always on. */
    @Transactional(readOnly = true)
    public boolean enabled(Long companyId, EChatChannel channel, String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return true;
        }
        return repo.findByCompanyIdAndChannelAndEventType(companyId, channel, eventType)
                .map(CompanyChannelEvent::isEnabled).orElse(true);
    }

    private ChannelEventsView view(Long cid, EChatChannel ch) {
        Map<String, Boolean> stored = repo.findByCompanyIdAndChannel(cid, ch).stream()
                .collect(Collectors.toMap(CompanyChannelEvent::getEventType, CompanyChannelEvent::isEnabled,
                        (a, b) -> a));
        Map<EChannelEvent.EModule, List<EventSwitch>> byModule = new LinkedHashMap<>();
        for (EChannelEvent.EModule m : EChannelEvent.EModule.values()) {
            byModule.put(m, new java.util.ArrayList<>());
        }
        for (EChannelEvent e : EChannelEvent.values()) {
            byModule.get(e.getModule()).add(new EventSwitch(e.getEventType(), e.getDisplayName(),
                    stored.getOrDefault(e.getEventType(), true)));
        }
        List<ModuleSwitches> modules = byModule.entrySet().stream().filter(x -> !x.getValue().isEmpty())
                .map(x -> new ModuleSwitches(x.getKey(), x.getValue())).toList();
        return new ChannelEventsView(ch, !stored.isEmpty(), modules);
    }

    private static EChatChannel channel(String path) {
        return Arrays.stream(EChatChannel.values()).filter(c -> c.path.equals(path)).findFirst()
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_020, path));
    }
}
