package com.gatto.tradestats.service;

import com.gatto.tradestats.domain.TradeStatsSnapshot;
import com.gatto.tradestats.domain.TradeStatsSnapshotRepository;
import com.gatto.tradestats.export.TradeStatsXlsxExporter;
import com.gatto.tradestats.pxweb.JsonStatDataset;
import com.gatto.tradestats.pxweb.PxWebClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TradeStatsImportService {

    private final PxWebClient pxWebClient;
    private final TradeStatsSnapshotRepository repository;
    private final TradeStatsXlsxExporter exporter;

    @Transactional
    public void importLatestMonth() {
        String rawJson = pxWebClient.fetchLatestMonth();
        JsonStatDataset dataset = JsonStatDataset.parse(rawJson);

        String period = dataset.period();
        String top10Json = dataset.top10PartnersAsJson(10);

        String monthlyRaw = pxWebClient.fetchMonthlyTotals(36);
        String monthlySeriesJson = JsonStatDataset.parse(monthlyRaw).monthlySeriesAsJson();

        TradeStatsSnapshot snapshot = repository.findByPeriod(period)
                .orElseGet(TradeStatsSnapshot::new);
        snapshot.setPeriod(period);
        snapshot.setRawJson(rawJson);
        snapshot.setTop10PartnersJson(top10Json);
        snapshot.setMonthlySeriesJson(monthlySeriesJson);
        snapshot.setFetchedAt(Instant.now());

        repository.save(snapshot);
        writeLatestXlsx(top10Json, monthlySeriesJson);
    }

    private void writeLatestXlsx(String top10Json, String monthlySeriesJson) {
        try {
            byte[] xlsx = exporter.toXlsx(top10Json, monthlySeriesJson);
            Path outputPath = Path.of("/opt/trade-stats/output/latest.xlsx");
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, xlsx);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write latest trade stats xlsx", e);
        }
    }
}
