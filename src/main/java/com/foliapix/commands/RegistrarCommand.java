package com.foliapix.commands;

import com.foliapix.FoliaPix;
import com.foliapix.http.HttpClientUtil;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class RegistrarCommand implements CommandExecutor {

    private static final Pattern CPF_PATTERN = Pattern.compile("^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$");

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] args) {
        if (!(s instanceof Player p)) return false;

        if (args.length != 1) {
            p.sendMessage("§cUso: /registrar <cpf> (Formato: 000.000.000-00)");
            return true;
        }

        String cpf = args[0];
        if (!CPF_PATTERN.matcher(cpf).matches()) {
            p.sendMessage("§cFormato de CPF inválido. Use pontos e traço (ex: 123.456.789-00).");
            return true;
        }

        String backendUrl = FoliaPix.getInstance().getConfig().getString("backend.url");
        String apiKey = FoliaPix.getInstance().getConfig().getString("security.api_key");

        if (backendUrl == null || backendUrl.isEmpty()) {
            p.sendMessage("§cURL do backend não configurada.");
            return true;
        }

        p.sendMessage("§eRegistrando CPF...");

        JsonObject json = new JsonObject();
        json.addProperty("uuid", p.getUniqueId().toString());
        json.addProperty("username", p.getName());
        json.addProperty("cpf", cpf);

        HttpClientUtil.postAsync(backendUrl + "/api/user/register", json.toString(), apiKey)
                .thenAccept(response -> {
                    org.bukkit.Bukkit.getScheduler().runTask(FoliaPix.getInstance(), () -> {
                        if (response != null && !response.contains("error")) {
                            p.sendMessage("§aCPF registrado com sucesso! Agora você pode vender itens.");
                        } else {
                            p.sendMessage("§cErro ao registrar: " + response);
                        }
                    });
                });

        return true;
    }
}
