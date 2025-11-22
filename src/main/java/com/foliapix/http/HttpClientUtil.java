package com.foliapix.http;

import com.foliapix.FoliaPix;
import java.net.URI;
import java.net.http.*;

public class HttpClientUtil {

    public static String post(String url, String json) {
        try {
            HttpClient c = HttpClient.newHttpClient();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .header("X-API-KEY", FoliaPix.getInstance().getConfig().getString("security.api_key"))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = c.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
