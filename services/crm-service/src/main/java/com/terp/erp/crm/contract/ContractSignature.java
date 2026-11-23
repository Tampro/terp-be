package com.terp.erp.crm.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

import java.time.Instant;

/**
 * Hand-drawn acceptance of a contract by one side (TERP Core ContractSignature). An electronic acceptance record,
 * not a qualified digital signature: nothing here is certified by a CA.
 */
@Getter
@Setter
@Entity
@Table(name = "contract_signature")
public class ContractSignature extends BaseAuditableEntity {
    public enum ESignerType { COMPANY, CLIENT }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signer_type", nullable = false, length = 10)
    private ESignerType signerType;

    /** IAM user id of the signer (company user or client portal user). */
    @Column(name = "signer_user_id")
    private Long signerUserId;

    @Column(name = "signer_name", nullable = false, length = 150)
    private String signerName;

    @Column(name = "signer_email")
    private String signerEmail;

    @Column(name = "signature_png", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] signaturePng;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;
}
