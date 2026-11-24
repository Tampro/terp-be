package com.terp.erp.crm.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.contract.ContractSignature.ESignerType;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Hand-drawn contract acceptance (TERP Core ContractSignatureController#store, Routes/web.php
 * {@code POST {contract}/signatures}). Like the reference, either side may sign first and the contract becomes
 * ACTIVE (reference status '1') once two signatures exist. Unlike the reference: one signature per side (the
 * reference keys by user and lets a user re-sign through updateOrCreate), a tenant/customer check on every call, a
 * content hash, and the signed fields are locked after the first signature.
 * An electronic acceptance record, not a qualified digital signature.
 */
@Service
@RequiredArgsConstructor
public class ContractSignatureService {
    static final String DISCLAIMER = "Electronic acceptance record, not a qualified digital signature.";
    private final ContractService contractService;
    private final ContractPortalService portal;
    private final ContractSignatureRepository signatures;
    private final ContractRepository contracts;
    private final Clock clock;

    public record SignForm(
            @Schema(description = "Hand-drawn signature: PNG as a data URL, decoded at most 200 KB and 2000 x 1000 px",
                    example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 300_000) String signatureDataUrl,
            @Schema(description = "Company side: name printed under the signature (default: my name from the token). "
                    + "Client side: ignored, the name comes from the token", example = "Test Signer")
            @Size(max = 150) String signerName) {
    }

    /** Where the request came from; recorded with the signature, never shown on the client portal. */
    public record Origin(String ip, String userAgent) {
        /**
         * X-Client-IP set by the gateway, else the connection address (a call that did not pass the gateway).
         * X-Forwarded-For is never read: the gateway passes it on as the client sent it.
         */
        public static Origin of(HttpServletRequest request) {
            String h = request.getHeader("X-Client-IP");
            return new Origin(h != null && !h.isBlank() ? h.trim() : request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        }
    }

    /** Company view of one signature. */
    public record SignatureDto(ESignerType signerType, String signerUserId, String signerName, String signerEmail,
                               Instant signedAt, String contentSha256, boolean contentMatches, String ip,
                               String userAgent) {
    }

    /** Client portal view: no IP, user agent, user id or e-mail. */
    public record PortalSignature(ESignerType signerType, String signerName, Instant signedAt,
                                  boolean contentMatches) {
    }

    // ---- company side ------------------------------------------------------------------------------------------

    // READ_COMMITTED: after waiting on the contract row lock, the signature count must see the other side's
    // committed row; under REPEATABLE READ the snapshot taken by the earlier access check would hide it.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<SignatureDto> signAsCompany(Long contractId, SignForm f, Origin origin) {
        Contract c = contractService.requireForWrite(contractId);
        String name = f.signerName() == null || f.signerName().isBlank() ? tokenName() : f.signerName().trim();
        sign(c, ESignerType.COMPANY, name, f.signatureDataUrl(), origin);
        return companyView(c);
    }

    @Transactional(readOnly = true)
    public List<SignatureDto> listForCompany(Long contractId) {
        return companyView(contractService.requireForView(contractId));
    }

    @Transactional(readOnly = true)
    public byte[] imageForCompany(Long contractId, ESignerType type) {
        return image(contractService.requireForView(contractId), type);
    }

    @Transactional(readOnly = true)
    public Pdf pdfForCompany(Long contractId) {
        return pdf(contractService.requireForView(contractId));
    }

    // ---- client portal -----------------------------------------------------------------------------------------

    // READ_COMMITTED: after waiting on the contract row lock, the signature count must see the other side's
    // committed row; under REPEATABLE READ the snapshot taken by the earlier access check would hide it.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<PortalSignature> signAsClient(Long contractId, SignForm f, Origin origin) {
        Contract c = portal.requireMine(contractId);
        // The client's identity comes from the token only; a body name could impersonate someone else.
        sign(c, ESignerType.CLIENT, tokenName(), f.signatureDataUrl(), origin);
        return portalView(c);
    }

    @Transactional(readOnly = true)
    public List<PortalSignature> listForClient(Long contractId) {
        return portalView(portal.requireMine(contractId));
    }

    @Transactional(readOnly = true)
    public byte[] imageForClient(Long contractId, ESignerType type) {
        return image(portal.requireMine(contractId), type);
    }

    @Transactional(readOnly = true)
    public Pdf pdfForClient(Long contractId) {
        return pdf(portal.requireMine(contractId));
    }

    public record Pdf(String fileName, byte[] content) {
    }

    // ---- shared ------------------------------------------------------------------------------------------------

    private void sign(Contract checked, ESignerType type, String name, String dataUrl, Origin origin) {
        // Access was checked on `checked`; re-read under a row lock so concurrent signatures can't both count 1
        // and leave a doubly signed contract PENDING.
        Contract c = contracts.lockById(checked.getId()).orElseThrow(() -> ApiException.notFound(EContractError.CTR_002));
        // TODO(assumption): only a PENDING contract is signable (the reference does not check the status).
        if (c.getStatus() != Contract.EStatus.PENDING) {
            throw ApiException.badRequest(EContractError.CTR_017);
        }
        if (signatures.existsByContractIdAndSignerType(c.getId(), type)) {
            throw ApiException.badRequest(EContractError.CTR_016);
        }
        byte[] png = SignaturePng.decode(dataUrl);
        ErpPrincipal p = CurrentUser.get();
        ContractSignature s = new ContractSignature();
        s.setCompanyId(c.getCompanyId());
        s.setContractId(c.getId());
        s.setSignerType(type);
        s.setSignerUserId(p.userId());
        s.setSignerName(truncate(name == null || name.isBlank() ? p.username() : name, 150));
        s.setSignerEmail(truncate(claim("email"), 255));
        s.setSignaturePng(png);
        s.setContentSha256(ContractContentHash.sha256(c));
        s.setIp(truncate(origin == null ? null : origin.ip(), 64));
        s.setUserAgent(truncate(origin == null ? null : origin.userAgent(), 255));
        s.setSignedAt(Instant.now(clock));
        try {
            signatures.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            // Two requests of the same side at once: the unique (contract_id, signer_type) key decides.
            throw ApiException.badRequest(EContractError.CTR_016);
        }
        // TODO(assumption): both sides are required and either may sign first; two signatures = ACTIVE.
        if (signatures.countByContractId(c.getId()) >= ESignerType.values().length) {
            c.setStatus(Contract.EStatus.ACTIVE);
        }
    }

    private byte[] image(Contract c, ESignerType type) {
        return signatures.findByContractIdAndSignerType(c.getId(), type)
                .orElseThrow(() -> ApiException.notFound(EContractError.CTR_019)).getSignaturePng();
    }

    private Pdf pdf(Contract c) {
        return new Pdf("contract-" + c.getNumber() + ".pdf",
                ContractPdf.render(c, ContractContentHash.sha256(c), signatures.findByContractIdOrderBySignedAtAsc(c.getId())));
    }

    private List<SignatureDto> companyView(Contract c) {
        String hash = ContractContentHash.sha256(c);
        return signatures.findByContractIdOrderBySignedAtAsc(c.getId()).stream()
                .map(s -> new SignatureDto(s.getSignerType(),
                        s.getSignerUserId() == null ? null : s.getSignerUserId().toString(), s.getSignerName(),
                        s.getSignerEmail(), s.getSignedAt(), s.getContentSha256(), hash.equals(s.getContentSha256()),
                        s.getIp(), s.getUserAgent()))
                .toList();
    }

    private List<PortalSignature> portalView(Contract c) {
        String hash = ContractContentHash.sha256(c);
        return signatures.findByContractIdOrderBySignedAtAsc(c.getId()).stream()
                .map(s -> new PortalSignature(s.getSignerType(), s.getSignerName(), s.getSignedAt(),
                        hash.equals(s.getContentSha256())))
                .toList();
    }

    /** Full name from the Keycloak token (name, else given + family), else the username. */
    static String tokenName() {
        String name = claim("name");
        if (name == null) {
            String given = claim("given_name");
            String family = claim("family_name");
            String joined = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
            name = joined.isEmpty() ? null : joined;
        }
        return name == null ? CurrentUser.get().username() : name;
    }

    private static String claim(String name) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof JwtAuthenticationToken t) {
            String v = t.getToken().getClaimAsString(name);
            return v == null || v.isBlank() ? null : v.trim();
        }
        return null;
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
