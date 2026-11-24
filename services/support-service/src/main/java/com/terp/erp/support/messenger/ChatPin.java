package com.terp.erp.support.messenger;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A conversation a user pinned to the top of their list (core parity, §18; personal, not shared with the peer). */
@Getter
@Setter
@Entity
@Table(name = "chat_pin")
public class ChatPin extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "peer_user_id", nullable = false)
    private Long peerUserId;
}
