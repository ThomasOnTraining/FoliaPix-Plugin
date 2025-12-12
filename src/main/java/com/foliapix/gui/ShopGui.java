package com.foliapix.gui;

import com.foliapix.FoliaPix;
import com.foliapix.data.AuctionItem;
import com.foliapix.http.HttpClientUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ShopGui extends PaginatedGui<AuctionItem> {

    private boolean sortCheapest = true; // Estado do filtro

    public ShopGui() {
        super("Loja FoliaPix", 54);
    }

    public void loadItems(Player p) {
        String backendUrl = FoliaPix.getInstance().getConfig().getString("backend.url");
        String apiKey = FoliaPix.getInstance().getConfig().getString("security.api_key");

        if (backendUrl == null || backendUrl.isEmpty()) {
            p.sendMessage("§cURL do backend não configurada.");
            return;
        }

        HttpClientUtil.postAsync(backendUrl + "/api/anuncio/listar", "{}", apiKey)
                .thenAccept(jsonStr -> {
                    if (jsonStr == null) {
                        p.sendMessage("§cErro ao carregar loja.");
                        return;
                    }

                    List<AuctionItem> items = new ArrayList<>();
                    try {
                        JsonArray array = JsonParser.parseString(jsonStr).getAsJsonArray();
                        for (JsonElement el : array) {
                            var obj = el.getAsJsonObject();
                            String b64 = obj.has("item_base64") ? obj.get("item_base64").getAsString() : null;
                            
                            items.add(new AuctionItem(
                                    obj.get("id").getAsInt(),
                                    obj.get("vendedor").getAsString(),
                                    obj.get("item").getAsString(),
                                    obj.get("preco").getAsDouble(),
                                    b64
                            ));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        p.sendMessage("§cErro ao processar dados da loja.");
                        return;
                    }

                    // Volta para thread principal para abrir o inventário
                    Bukkit.getScheduler().runTask(FoliaPix.getInstance(), () -> {
                        this.setItems(items);
                        this.applySort(); // Ordena antes de abrir
                        this.open(p);
                    });
                });
    }

    private void applySort() {
        if (items == null) return;
        
        items.sort(Comparator.comparingDouble(AuctionItem::getPreco));
        if (!sortCheapest) {
            items.reversed(); // Inverte se for "Mais Caro"
        }
        updatePage();
    }
    
    @Override
    protected void updatePage() {
        super.updatePage();
        
        // Botão de Filtro (Slot 49 - Centro embaixo)
        ItemStack filterBtn = new ItemStack(Material.EMERALD);
        ItemMeta meta = filterBtn.getItemMeta();
        meta.setDisplayName(sortCheapest ? "§aOrd: §2Mais Baratos" : "§cOrd: §4Mais Caros");
        List<String> lore = new ArrayList<>();
        lore.add("§7Clique para alternar a ordem.");
        meta.setLore(lore);
        filterBtn.setItemMeta(meta);
        
        inventory.setItem(49, filterBtn);
    }
    
    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        super.onClick(e); // Lida com itens e paginação

        if (e.getRawSlot() == 49) {
            sortCheapest = !sortCheapest;
            applySort();
            // Som para feedback suave
            if (e.getWhoClicked() instanceof Player p) {
                p.playNote(p.getLocation(), org.bukkit.Instrument.PIANO, org.bukkit.Note.natural(1, org.bukkit.Note.Tone.C));
            }
        }
    }

    @Override
    protected ItemStack createIcon(AuctionItem item) {
        ItemStack icon;

        // Tenta restaurar do Base64 (NBT Completo)
        if (item.getItemBase64() != null && !item.getItemBase64().isEmpty()) {
            icon = com.foliapix.utils.ItemSerializer.fromBase64(item.getItemBase64());
        } else {
            // Fallback para nome material simples
            Material mat = Material.matchMaterial(item.getItem());
            if (mat == null) mat = Material.PAPER;
            icon = new ItemStack(mat);
        }
        
        if (icon == null) icon = new ItemStack(Material.BARRIER);

        ItemMeta meta = icon.getItemMeta();
        // Preserva o DisplayName original do item se tiver, mas adiciona o lore da loja
        
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add("§8----------------");
        lore.add("§7Vendedor: §f" + item.getVendedor());
        lore.add("§7Preço: §2R$ " + String.format("%.2f", item.getPreco()));
        lore.add("");
        lore.add("§eClique para comprar!");
        
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @Override
    protected void onItemClick(AuctionItem item, Player p) {
        // Lógica de compra (mantida igual)
        p.closeInventory();
        p.sendMessage("§eIniciando compra...");

        String backendUrl = FoliaPix.getInstance().getConfig().getString("backend.url");
        String apiKey = FoliaPix.getInstance().getConfig().getString("security.api_key");

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("auction_id", item.getId());
        json.addProperty("buyer_uuid", p.getUniqueId().toString());

        HttpClientUtil.postAsync(backendUrl + "/api/anuncio/comprar", json.toString(), apiKey)
                .thenAccept(resp -> {
                    Bukkit.getScheduler().runTask(FoliaPix.getInstance(), () -> {
                        if (resp == null) {
                            p.sendMessage("§cErro ao conectar com backend.");
                            return;
                        }

                        try {
                            var obj = JsonParser.parseString(resp).getAsJsonObject();
                            if (obj.has("pix_code")) {
                                String pix = obj.get("pix_code").getAsString();
                                p.sendMessage("§a§lPAGAMENTO GERADO!");
                                p.sendMessage("§7Copie e cole no seu banco:");
                                
                                net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text("§b[CLIQUE PARA COPIAR]")
                                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(pix))
                                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("§7" + pix)));
                                
                                p.sendMessage(message);
                                p.sendMessage("§7Aguardando confirmação...");

                                if (obj.has("pix_base64")) {
                                    String base64 = obj.get("pix_base64").getAsString();
                                    org.bukkit.inventory.ItemStack map = com.foliapix.utils.QRCodeRenderer.createMapFromBase64(base64);
                                    if (map != null) {
                                        p.getInventory().addItem(map);
                                        p.sendMessage("§a§lUm mapa com o QR Code foi adicionado ao seu inventário!");
                                        p.sendMessage("§7(Segure o mapa para escanear com o celular)");
                                    }
                                }
                            } else {
                                p.sendMessage("§cErro: " + resp);
                            }
                        } catch (Exception e) {
                            p.sendMessage("§cErro ao ler resposta.");
                        }
                    });
                });
    }
}
