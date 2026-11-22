package com.terp.erp.support.messenger;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A contact a user marked as favorite (reference ch_favorites: user_id, favorite_id). */
@Getter
@Setter
@Entity
@Table(name = "chat_favorite")
public class ChatFavorite extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "favorite_user_id", nullable = false)
    private Long favoriteUserId;
}
