package com.gatto.tradestats.web;

import com.gatto.tradestats.domain.TradeStatsSnapshotRepository;
import com.gatto.tradestats.export.TradeStatsXlsxExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade-stats")
@RequiredArgsConstructor
public class TradeStatsController {

    private final TradeStatsSnapshotRepository repository;
    private final TradeStatsXlsxExporter exporter;

    @GetMapping("/latest.xlsx")
    public ResponseEntity<byte[]> latest() {
        var snapshot = repository.findTopByOrderByPeriodDesc().orElseThrow();
        byte[] xlsx = exporter.toXlsx(snapshot.getTop10PartnersJson());

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=trade-stats-" + snapshot.getPeriod() + ".xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(xlsx);
    }
}