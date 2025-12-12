package com.foliapix.http;

import com.foliapix.FoliaPix;
import com.sun.net.httpserver.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.InetSocketAddress;

public class PluginHttpServer {

    private HttpServer server;
    private final String apiKey;

    public PluginHttpServer(int port, String apiKey) {
        this.apiKey = apiKey;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        server.createContext("/pagamento/confirmado", this::handle);
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {

        if (!"POST".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, 0);
            return;
        }

        String k = ex.getRequestHeaders().getFirst("X-API-KEY");
        if (k == null || !k.equals(apiKey)) {
            ex.sendResponseHeaders(401, 0);
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes());

        try {
             com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
             String player = json.get("player").getAsString();
             String item = json.get("item").getAsString();
             int amount = json.get("amount").getAsInt();

             Bukkit.getScheduler().runTask(FoliaPix.getInstance(), () -> {
                 Player p = Bukkit.getPlayer(player);
                 if (p != null) {
                     p.getInventory().addItem(
                             new org.bukkit.inventory.ItemStack(
                                     org.bukkit.Material.valueOf(item),
                                     amount
                             )
                     );
                 }
             });
        } catch (Exception e) {
            e.printStackTrace();
            ex.sendResponseHeaders(400, 0);
            return;
        }

        String ok = "{\"status\":\"entregue\"}";
        ex.sendResponseHeaders(200, ok.length());
        try (OutputStream os = ex.getResponseBody()) {
            os.write(ok.getBytes());
        }
    }

    public void stop() {
        server.stop(0);
    }
}
