package com.gatto.tradestats.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builds an .xlsx workbook from the JSON produced by
 * {@link com.gatto.tradestats.pxweb.JsonStatDataset#top10PartnersAsJson(int)}.
 * Output: one sheet per flow (Export / Import), each with columns
 * Riik | Väärtus (mln €) | Osatähtsus (%) | Muutus aastaga (%)
 * — ready to paste into Excel/Datawrapper/Flourish for charting.
 */
@Component
public class TradeStatsXlsxExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] HEADERS = {
            "Riik", "Väärtus (mln €)", "Osatähtsus (%)", "Muutus aastaga (%)"
    };

    public byte[] toXlsx(String top10PartnersJson, String monthlySeriesJson) {
        try {
            JsonNode root = MAPPER.readTree(top10PartnersJson);
            String period = root.get("period").asString();
            JsonNode monthlyRoot = monthlySeriesJson == null ? null : MAPPER.readTree(monthlySeriesJson);

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                CellStyle titleStyle = titleStyle(workbook);
                CellStyle headerStyle = headerStyle(workbook);
                CellStyle numberStyle = numberStyle(workbook, "0.0");

                buildSheet(workbook, "Eksport", root.get("exportTop10"), period,
                        titleStyle, headerStyle, numberStyle);
                buildSheet(workbook, "Import", root.get("importTop10"), period,
                        titleStyle, headerStyle, numberStyle);
                if (monthlyRoot != null) {
                    buildMonthlySheet(workbook, monthlyRoot, titleStyle, headerStyle, numberStyle);
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build xlsx from trade stats JSON", e);
        }
    }

    private void buildSheet(XSSFWorkbook workbook, String sheetName, JsonNode rows, String period,
                            CellStyle titleStyle, CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet(sheetName);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Eesti peamised väliskaubanduspartnerid — " + sheetName + ", " + period);
        titleCell.setCellStyle(titleStyle);

        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 3;
        if (rows != null) {
            for (JsonNode row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);

                dataRow.createCell(0).setCellValue(row.get("countryName").asString());

                Cell valueCell = dataRow.createCell(1);
                setNumericOrBlank(valueCell, row.get("valueMillionEur"), numberStyle);

                Cell shareCell = dataRow.createCell(2);
                setNumericOrBlank(shareCell, row.get("sharePercent"), numberStyle);

                Cell yoyCell = dataRow.createCell(3);
                setNumericOrBlank(yoyCell, row.get("yoyChangePercent"), numberStyle);
            }
        }

        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.setColumnWidth(0, Math.max(sheet.getColumnWidth(0), 20 * 256));
    }

    private void setNumericOrBlank(Cell cell, JsonNode node, CellStyle numberStyle) {
        if (node == null || node.isNull() || (node.isDouble() && Double.isNaN(node.asDouble()))) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(node.asDouble());
        cell.setCellStyle(numberStyle);
    }

    private CellStyle titleStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle numberStyle(XSSFWorkbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    private void buildMonthlySheet(XSSFWorkbook workbook, JsonNode monthlyRoot,
                                   CellStyle titleStyle, CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("Kaubavahetus kuude kaupa");

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Eesti kaubavahetus kuude kaupa");
        titleRow.getCell(0).setCellStyle(titleStyle);

        String[] headers = {"Kuu", "Eksport (mln €)", "Import (mln €)", "Bilanss (mln €)"};
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIndex = 3;
        for (JsonNode row : monthlyRoot.get("months")) {
            Row dataRow = sheet.createRow(rowIndex++);
            dataRow.createCell(0).setCellValue(row.get("period").asString());
            setNumericOrBlank(dataRow.createCell(1), row.get("exportMillionEur"), numberStyle);
            setNumericOrBlank(dataRow.createCell(2), row.get("importMillionEur"), numberStyle);
            setNumericOrBlank(dataRow.createCell(3), row.get("balanceMillionEur"), numberStyle);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }
}
