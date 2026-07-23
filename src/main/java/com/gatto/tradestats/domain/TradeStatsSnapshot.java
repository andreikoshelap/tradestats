package com.gatto.tradestats.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class TradeStatsSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String period;              // "2026M05"

    @Lob private String rawJson;
    @Lob private String top10PartnersJson; // уже отсортированный топ-10

    private Instant fetchedAt;

}