package com.gatto.tradestats.pxweb;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PxWebClient {

    private final RestClient restClient = RestClient.create();
    private static final String TABLE_URL = "https://andmed.stat.ee/api/v1/en/statsql/VKK12";

    public String fetchLatestMonth() {
        String body = """
            {
              "query": [
                { "code": "ContentsCode", "selection": { "filter": "item", "values": ["TRD_VAL","COUNTRY_SHARE","TRD_VAL_SPREV"] } },
                { "code": "FLOW", "selection": { "filter": "item", "values": ["EXP","IMP"] } },
                { "code": "PART_COUNTRY", "selection": { "filter": "all", "values": ["*"] } },
                { "code": "TIME", "selection": { "filter": "top", "values": ["1"] } }
              ],
              "response": { "format": "json-stat2" }
            }
            """;

        return restClient.post()
                .uri(TABLE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String fetchMonthlyTotals(int monthsBack) {
        String body = """
        {
          "query": [
            { "code": "ContentsCode", "selection": { "filter": "item", "values": ["TRD_VAL"] } },
            { "code": "FLOW", "selection": { "filter": "item", "values": ["EXP","IMP","BAL"] } },
            { "code": "PART_COUNTRY", "selection": { "filter": "item", "values": ["TOTAL"] } },
            { "code": "TIME", "selection": { "filter": "top", "values": ["%d"] } }
          ],
          "response": { "format": "json-stat2" }
        }
        """.formatted(monthsBack);

        return restClient.post()
                .uri(TABLE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}