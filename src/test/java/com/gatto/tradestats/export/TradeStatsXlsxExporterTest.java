package com.gatto.tradestats.export;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TradeStatsXlsxExporterTest {

    private final TradeStatsXlsxExporter exporter = new TradeStatsXlsxExporter();

    @Test
    void toXlsxAddsMonthlySeriesAsThirdSheet() throws Exception {
        byte[] xlsx = exporter.toXlsx(top10PartnersJson(), monthlySeriesJson());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("Eksport");
            assertThat(workbook.getSheetName(1)).isEqualTo("Import");
            assertThat(workbook.getSheetName(2)).isEqualTo("Kaubavahetus kuude kaupa");

            var monthlySheet = workbook.getSheetAt(2);
            assertThat(monthlySheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Eesti kaubavahetus kuude kaupa");
            assertThat(monthlySheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Kuu");
            assertThat(monthlySheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("2026M04");
            assertThat(monthlySheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(110.0);
            assertThat(monthlySheet.getRow(4).getCell(3).getNumericCellValue()).isEqualTo(15.0);
        }
    }

    private static String top10PartnersJson() {
        return """
                {
                  "period": "2026M05",
                  "exportTop10": [
                    {
                      "countryCode": "FIN",
                      "countryName": "Finland",
                      "valueMillionEur": 200.0,
                      "sharePercent": 12.3,
                      "yoyChangePercent": -1.2
                    }
                  ],
                  "importTop10": [
                    {
                      "countryCode": "SWE",
                      "countryName": "Sweden",
                      "valueMillionEur": 300.0,
                      "sharePercent": 15.7,
                      "yoyChangePercent": -3.2
                    }
                  ]
                }
                """;
    }

    private static String monthlySeriesJson() {
        return """
                {
                  "tableTitle": "Eesti kaubavahetus kuude kaupa",
                  "months": [
                    {
                      "period": "2026M04",
                      "exportMillionEur": 110.0,
                      "importMillionEur": 100.0,
                      "balanceMillionEur": 10.0
                    },
                    {
                      "period": "2026M05",
                      "exportMillionEur": 120.0,
                      "importMillionEur": 105.0,
                      "balanceMillionEur": 15.0
                    }
                  ]
                }
                """;
    }
}
