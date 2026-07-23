package com.gatto.tradestats.scheduler;

import com.gatto.tradestats.service.TradeStatsImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeStatsScheduler {

    private final TradeStatsImportService importService;

    @Scheduled(cron = "0 0 9 11 * *", zone = "Europe/Tallinn")
    public void importMonthly() {
        importService.importLatestMonth();
    }
}