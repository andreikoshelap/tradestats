package com.gatto.tradestats.service;

import com.gatto.tradestats.domain.TradeStatsSnapshot;
import com.gatto.tradestats.domain.TradeStatsSnapshotRepository;
import com.gatto.tradestats.export.TradeStatsXlsxExporter;
import com.gatto.tradestats.pxweb.PxWebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeStatsImportServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PxWebClient pxWebClient = org.mockito.Mockito.mock(PxWebClient.class);
    private final TradeStatsSnapshotRepository repository = org.mockito.Mockito.mock(TradeStatsSnapshotRepository.class);
    private final TradeStatsXlsxExporter exporter = org.mockito.Mockito.mock(TradeStatsXlsxExporter.class);
    private final TradeStatsImportService service = new TradeStatsImportService(pxWebClient, repository, exporter);

    @Test
    void importLatestMonthCreatesSnapshotWhenPeriodIsNew() throws Exception {
        String latestMonthJson = latestMonthJson();
        String monthlyTotalsJson = monthlyTotalsJson();
        when(pxWebClient.fetchLatestMonth()).thenReturn(latestMonthJson);
        when(pxWebClient.fetchMonthlyTotals(36)).thenReturn(monthlyTotalsJson);
        when(repository.findByPeriod("2026M05")).thenReturn(Optional.empty());
        when(exporter.toXlsx(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new byte[]{1, 2, 3});

        Instant beforeImport = Instant.now();

        service.importLatestMonth();

        ArgumentCaptor<TradeStatsSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TradeStatsSnapshot.class);
        verify(repository).save(snapshotCaptor.capture());

        TradeStatsSnapshot saved = snapshotCaptor.getValue();
        assertThat(saved.getPeriod()).isEqualTo("2026M05");
        assertThat(saved.getRawJson()).isEqualTo(latestMonthJson);
        assertThat(saved.getFetchedAt()).isAfterOrEqualTo(beforeImport);

        JsonNode top10 = MAPPER.readTree(saved.getTop10PartnersJson());
        assertThat(top10.get("period").asString()).isEqualTo("2026M05");
        assertThat(top10.get("exportTop10")).hasSize(2);
        assertThat(top10.get("exportTop10").get(0).get("countryCode").asString()).isEqualTo("FIN");
        assertThat(top10.get("exportTop10").get(0).get("valueMillionEur").asDouble()).isEqualTo(200.0);
        assertThat(top10.get("exportTop10").get(0).get("sharePercent").asDouble()).isEqualTo(12.3);
        assertThat(top10.get("exportTop10").get(1).get("countryCode").asString()).isEqualTo("SWE");
        assertThat(top10.get("importTop10").get(0).get("countryCode").asString()).isEqualTo("SWE");

        JsonNode monthlySeries = MAPPER.readTree(saved.getMonthlySeriesJson());
        assertThat(monthlySeries.get("months")).hasSize(2);
        assertThat(monthlySeries.get("months").get(0).get("period").asString()).isEqualTo("2026M04");
        assertThat(monthlySeries.get("months").get(0).get("exportMillionEur").asDouble()).isEqualTo(110.0);
        assertThat(monthlySeries.get("months").get(1).get("balanceMillionEur").asDouble()).isEqualTo(15.0);
    }

    @Test
    void importLatestMonthUpdatesExistingSnapshotForSamePeriod() {
        String latestMonthJson = latestMonthJson();
        String monthlyTotalsJson = monthlyTotalsJson();
        TradeStatsSnapshot existing = new TradeStatsSnapshot();
        existing.setId(42L);
        existing.setPeriod("2026M05");
        existing.setRawJson("old raw json");
        existing.setTop10PartnersJson("old top json");
        existing.setMonthlySeriesJson("old monthly json");
        existing.setFetchedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(pxWebClient.fetchLatestMonth()).thenReturn(latestMonthJson);
        when(pxWebClient.fetchMonthlyTotals(36)).thenReturn(monthlyTotalsJson);
        when(repository.findByPeriod("2026M05")).thenReturn(Optional.of(existing));
        when(exporter.toXlsx(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new byte[]{1, 2, 3});

        service.importLatestMonth();

        verify(repository).save(existing);
        assertThat(existing.getId()).isEqualTo(42L);
        assertThat(existing.getPeriod()).isEqualTo("2026M05");
        assertThat(existing.getRawJson()).isEqualTo(latestMonthJson);
        assertThat(existing.getTop10PartnersJson()).contains("\"exportTop10\"");
        assertThat(existing.getMonthlySeriesJson()).contains("\"months\"");
        assertThat(existing.getFetchedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static String latestMonthJson() {
        return """
                {
                  "id": ["ContentsCode", "FLOW", "PART_COUNTRY", "TIME"],
                  "size": [3, 2, 4, 1],
                  "dimension": {
                    "ContentsCode": {
                      "category": {
                        "index": {"TRD_VAL": 0, "COUNTRY_SHARE": 1, "TRD_VAL_SPREV": 2},
                        "label": {
                          "TRD_VAL": "Trade value",
                          "COUNTRY_SHARE": "Share",
                          "TRD_VAL_SPREV": "Change from previous year"
                        }
                      }
                    },
                    "FLOW": {
                      "category": {
                        "index": {"EXP": 0, "IMP": 1},
                        "label": {"EXP": "Export", "IMP": "Import"}
                      }
                    },
                    "PART_COUNTRY": {
                      "category": {
                        "index": {"TOTAL": 0, "EU": 1, "FIN": 2, "SWE": 3},
                        "label": {
                          "TOTAL": "Total",
                          "EU": "European Union",
                          "FIN": "Finland",
                          "SWE": "Sweden"
                        }
                      }
                    },
                    "TIME": {
                      "category": {
                        "index": {"2026M05": 0},
                        "label": {"2026M05": "May 2026"}
                      }
                    }
                  },
                  "value": [
                    999000000, 888000000, 200000000, 100000000,
                    999000000, 888000000, 50000000, 300000000,
                    null, null, 12.34, 5.55,
                    null, null, 4.44, 15.67,
                    null, null, -1.25, 2.75,
                    null, null, 9.87, -3.21
                  ]
                }
                """;
    }

    private static String monthlyTotalsJson() {
        return """
                {
                  "id": ["ContentsCode", "FLOW", "PART_COUNTRY", "TIME"],
                  "size": [1, 3, 1, 2],
                  "dimension": {
                    "ContentsCode": {
                      "category": {
                        "index": {"TRD_VAL": 0},
                        "label": {"TRD_VAL": "Trade value"}
                      }
                    },
                    "FLOW": {
                      "category": {
                        "index": {"EXP": 0, "IMP": 1, "BAL": 2},
                        "label": {"EXP": "Export", "IMP": "Import", "BAL": "Balance"}
                      }
                    },
                    "PART_COUNTRY": {
                      "category": {
                        "index": {"TOTAL": 0},
                        "label": {"TOTAL": "Total"}
                      }
                    },
                    "TIME": {
                      "category": {
                        "index": {"2026M04": 0, "2026M05": 1},
                        "label": {"2026M04": "April 2026", "2026M05": "May 2026"}
                      }
                    }
                  },
                  "value": [
                    110000000, 120000000,
                    100000000, 105000000,
                    10000000, 15000000
                  ]
                }
                """;
    }
}
