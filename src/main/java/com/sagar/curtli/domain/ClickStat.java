package com.sagar.curtli.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "click_stats",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"link_id", "bucket_hour"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Maps the foreign key back to the links table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private Link link;

    @Column(name = "bucket_hour", nullable = false)
    private OffsetDateTime bucketHour;

    @Column(name = "click_count", nullable = false)
    private long clickCount;
}