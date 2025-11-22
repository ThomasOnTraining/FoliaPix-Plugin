package com.foliapix.commands;

import com.foliapix.http.HttpClientUtil;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class AnunciarCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] args) {
        if (!(s instanceof Player p)) return false;

        if (args.length != 1) {
            p.sendMessage("§cUso: /anunciar <preço>");
            return true;
        }

        double preco = Double.parseDouble(args[0]);
        Material item = p.getInventory().getItemInMainHand().getType();

        String json = """
        {
          "vendedor": "%s",
          "item": "%s",
          "preco": %.2f
        }
        """.formatted(p.getName(), item.name(), preco);

        HttpClientUtil.post(
                "https://SEU_BACKEND/api/anuncio/criar",
                json
        );

        p.sendMessage("§aAnúncio enviado ao backend!");
        return true;
    }
}
