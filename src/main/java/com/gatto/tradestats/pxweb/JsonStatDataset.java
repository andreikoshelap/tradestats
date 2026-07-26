package com.gatto.tradestats.pxweb;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses a json-stat2 response from the PxWeb API (andmed.stat.ee, table VKK12)
 * and extracts a "top N trade partners" view for a single reporting month.
 */
public class JsonStatDataset {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Aggregate rows present in PART_COUNTRY that must not appear in a "top partners" ranking.
    private static final Set<String> AGGREGATE_COUNTRY_CODES = Set.of("TOTAL", "EU");

    private final List<String> dimensionIds;
    private final List<Integer> sizes;
    private final Map<String, Dimension> dimensions;
    private final double[] values;

    private JsonStatDataset(List<String> dimensionIds, List<Integer> sizes,
                            Map<String, Dimension> dimensions, double[] values) {
        this.dimensionIds = dimensionIds;
        this.sizes = sizes;
        this.dimensions = dimensions;
        this.values = values;
    }

    public static JsonStatDataset parse(String rawJson) {
        try {
            JsonNode root = MAPPER.readTree(rawJson);

            List<String> ids = new ArrayList<>();
            root.get("id").forEach(n -> ids.add(n.asString()));

            List<Integer> sizes = new ArrayList<>();
            root.get("size").forEach(n -> sizes.add(n.asInt()));

            JsonNode dimensionNode = root.get("dimension");
            Map<String, Dimension> dims = new LinkedHashMap<>();
            for (String id : ids) {
                dims.put(id, Dimension.parse(dimensionNode.get(id)));
            }

            JsonNode valueNode = root.get("value");
            double[] values = new double[valueNode.size()];
            int i = 0;
            for (JsonNode v : valueNode) {
                values[i++] = v.isNull() ? Double.NaN : v.asDouble();
            }

            return new JsonStatDataset(ids, sizes, dims, values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse json-stat2 response from PxWeb", e);
        }
    }

    /** The single TIME value present in the response, e.g. "2026M05". */
    public String period() {
        return dimensions.get("TIME").codesInOrder().getFirst();
    }

    /** Builds a JSON payload with top-N export and import partners for period(). */
    public String top10PartnersAsJson(int topN) {
        Map<String, List<Cell>> byFlowAndContent = flatten().stream()
                .collect(Collectors.groupingBy(c -> c.flow + "|" + c.contentsCode));

        ObjectNode root = MAPPER.createObjectNode();
        root.put("period", period());
        root.putIfAbsent("exportTop" + topN, buildFlowSection(byFlowAndContent, "EXP", topN));
        root.putIfAbsent("importTop" + topN, buildFlowSection(byFlowAndContent, "IMP", topN));

        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private ArrayNode buildFlowSection(Map<String, List<Cell>> byFlowAndContent, String flow, int topN) {
        Map<String, Double> value = indexByCountry(byFlowAndContent.get(flow + "|TRD_VAL"));
        Map<String, Double> share = indexByCountry(byFlowAndContent.get(flow + "|COUNTRY_SHARE"));
        Map<String, Double> yoyChange = indexByCountry(byFlowAndContent.get(flow + "|TRD_VAL_SPREV"));

        ArrayNode array = MAPPER.createArrayNode();
        Dimension countryDim = dimensions.get("PART_COUNTRY");

        value.entrySet().stream()
                .filter(e -> !AGGREGATE_COUNTRY_CODES.contains(e.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .forEach(e -> {
                    ObjectNode row = array.addObject();
                    row.put("countryCode", e.getKey());
                    row.put("countryName", countryDim.label(e.getKey()));
                    row.put("valueMillionEur", round(e.getValue() / 1_000_000));
                    row.put("sharePercent", round(share.getOrDefault(e.getKey(), Double.NaN)));
                    row.put("yoyChangePercent", round(yoyChange.getOrDefault(e.getKey(), Double.NaN)));
                });

        return array;
    }

    private Map<String, Double> indexByCountry(List<Cell> cells) {
        if (cells == null) return Map.of();
        return cells.stream().collect(Collectors.toMap(c -> c.country, c -> c.value, (a, b) -> a));
    }

    private double round(double v) {
        return Double.isNaN(v) ? v : Math.round(v * 10.0) / 10.0;
    }

    /** Expands the flat value[] array into individual cells with decoded dimension codes. */
    private List<Cell> flatten() {
        int dims = sizes.size();
        int[] strides = new int[dims];
        int stride = 1;
        for (int i = dims - 1; i >= 0; i--) {
            strides[i] = stride;
            stride *= sizes.get(i);
        }

        List<Cell> result = new ArrayList<>();
        int[] indices = new int[dims];

        for (int flat = 0; flat < values.length; flat++) {
            int remainder = flat;
            for (int d = 0; d < dims; d++) {
                indices[d] = remainder / strides[d];
                remainder %= strides[d];
            }

            double val = values[flat];
            if (Double.isNaN(val)) continue;

            String contentsCode = null, flow = null, country = null, time = null;
            for (int d = 0; d < dimensionIds.size(); d++) {
                String dimId = dimensionIds.get(d);
                String code = dimensions.get(dimId).codesInOrder().get(indices[d]);
                switch (dimId) {
                    case "ContentsCode" -> contentsCode = code;
                    case "FLOW" -> flow = code;
                    case "PART_COUNTRY" -> country = code;
                    case "TIME" -> time = code;
                    default -> { /* unknown dimension, ignore */ }
                }
            }

            if (flow != null && country != null && time != null) {
                result.add(new Cell(contentsCode, flow, country, time, val));
            }
        }

        return result;
    }

    private record Cell(String contentsCode, String flow, String country, String time, double value) {}

    /** One json-stat2 dimension: ordered list of category codes + code→label map. */
    private static final class Dimension {
        private final List<String> codesInOrder;
        private final Map<String, String> labels;

        private Dimension(List<String> codesInOrder, Map<String, String> labels) {
            this.codesInOrder = codesInOrder;
            this.labels = labels;
        }

        static Dimension parse(JsonNode dimNode) {
            JsonNode category = dimNode.get("category");
            JsonNode indexNode = category.get("index");
            JsonNode labelNode = category.get("label");

            List<String> codes;
            if (indexNode != null && indexNode.isObject()) {
                codes = new ArrayList<>(Collections.nCopies(indexNode.size(), null));
                indexNode.properties().forEach(e -> codes.set(e.getValue().asInt(), e.getKey()));
            } else if (indexNode != null && indexNode.isArray()) {
                codes = new ArrayList<>();
                indexNode.forEach(n -> codes.add(n.asString()));
            } else {
                // no explicit index (single-value dimensions like TIME sometimes omit it)
                codes = new ArrayList<>();
                if (labelNode != null) {
                    codes.addAll(labelNode.propertyNames());
                }
            }

            Map<String, String> labels = new HashMap<>();
            if (labelNode != null) {
                labelNode.properties().forEach(e -> labels.put(e.getKey(), e.getValue().asString()));
            }

            return new Dimension(codes, labels);
        }

        List<String> codesInOrder() { return codesInOrder; }
        String label(String code) { return labels.getOrDefault(code, code); }
    }

    public String monthlySeriesAsJson() {
        Map<String, Map<String, Double>> byMonth = new TreeMap<>(); // period -> flow -> value

        for (Cell cell : flatten()) {
            byMonth.computeIfAbsent(cell.time(), k -> new HashMap<>())
                    .put(cell.flow(), cell.value());
        }

        ArrayNode array = MAPPER.createArrayNode();
        byMonth.forEach((period, flows) -> {
            double exportMillionEur = millionEur(flows.get("EXP"));
            double importMillionEur = millionEur(flows.get("IMP"));
            double balanceMillionEur = millionEur(flows.get("BAL"));
            if (Double.isNaN(balanceMillionEur) && !Double.isNaN(exportMillionEur) && !Double.isNaN(importMillionEur)) {
                balanceMillionEur = round(exportMillionEur - importMillionEur);
            }

            ObjectNode row = array.addObject();
            row.put("period", period);
            row.put("exportMillionEur", exportMillionEur);
            row.put("importMillionEur", importMillionEur);
            row.put("balanceMillionEur", balanceMillionEur);
        });

        ObjectNode root = MAPPER.createObjectNode();
        root.put("tableTitle", "Eesti kaubavahetus kuude kaupa");
        root.set("months", array);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private double millionEur(Double value) {
        return value == null ? Double.NaN : round(value / 1_000_000);
    }


}
