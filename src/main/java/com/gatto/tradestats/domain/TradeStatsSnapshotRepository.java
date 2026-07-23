package com.gatto.tradestats.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface TradeStatsSnapshotRepository extends JpaRepository<TradeStatsSnapshot, Long> {

    Optional<TradeStatsSnapshot> findTopByOrderByPeriodDesc();

    Optional<TradeStatsSnapshot> findByPeriod(String period);
}
