package com.foliapix.commands;

import com.foliapix.http.HttpClientUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class LojaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] args) {
        if (!(s instanceof Player p)) return false;

        p.sendMessage("§eAbrindo loja...");
        new com.foliapix.gui.ShopGui().loadItems(p);

        return true;
    }
}
