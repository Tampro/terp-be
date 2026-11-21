package com.terp.erp.employee.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;

/** The rating of one indicator in a review; the indicator is copied so the review never changes afterwards. */
@Getter
@Setter
@Entity
@Table(name = "review_rating")
public class ReviewRating extends BaseAuditableEntity {
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "indicator_id", nullable = false)
    private Long indicatorId;

    @Column(name = "category_name", length = 150)
    private String categoryName;

    @Column(name = "indicator_name", nullable = false, length = 150)
    private String indicatorName;

    @Column(name = "indicator_description", length = 1000)
    private String indicatorDescription;

    @Column(name = "measurement_unit", length = 50)
    private String measurementUnit;

    @Column(name = "target_value", length = 100)
    private String targetValue;

    @Column(name = "weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    /** 1 (Poor) – 5 (Excellent); null = not rated yet. */
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;
}
