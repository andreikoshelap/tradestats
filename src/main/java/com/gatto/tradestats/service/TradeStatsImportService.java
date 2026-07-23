package com.gatto.tradestats.service;

import com.gatto.tradestats.domain.TradeStatsSnapshot;
import com.gatto.tradestats.domain.TradeStatsSnapshotRepository;
import com.gatto.tradestats.pxweb.JsonStatDataset;
import com.gatto.tradestats.pxweb.PxWebClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TradeStatsImportService {

    private final PxWebClient pxWebClient;
    private final TradeStatsSnapshotRepository repository;

    @Transactional
    public void importLatestMonth() {
        String rawJson = pxWebClient.fetchLatestMonth();
        JsonStatDataset dataset = JsonStatDataset.parse(rawJson);

        String period = dataset.period();
        String top10Json = dataset.top10PartnersAsJson(10); // сортировка по TRD_VAL, отдельно EXP/IMP

        TradeStatsSnapshot snapshot = repository.findByPeriod(period)
                .orElseGet(TradeStatsSnapshot::new);
        snapshot.setPeriod(period);
        snapshot.setRawJson(rawJson);
        snapshot.setTop10PartnersJson(top10Json);
        snapshot.setFetchedAt(Instant.now());
        repository.save(snapshot);
    }
}