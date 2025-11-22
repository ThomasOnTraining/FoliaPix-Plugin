package com.foliapix;

import com.foliapix.commands.AnunciarCommand;
import com.foliapix.commands.LojaCommand;
import com.foliapix.http.PluginHttpServer;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaPix extends JavaPlugin {

    private static FoliaPix instance;
    private PluginHttpServer httpServer;

    public static FoliaPix getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getCommand("anunciar").setExecutor(new AnunciarCommand());
        getCommand("loja").setExecutor(new LojaCommand());

        httpServer = new PluginHttpServer(
                getConfig().getInt("http.port"),
                getConfig().getString("security.api_key")
        );
        httpServer.start();

        getLogger().info("FoliaPix iniciado!");
    }

    @Override
    public void onDisable() {
        if (httpServer != null) httpServer.stop();
    }
}
