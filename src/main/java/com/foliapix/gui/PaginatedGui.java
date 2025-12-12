package com.foliapix.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class PaginatedGui<T> extends Gui {

    protected List<T> items;
    protected int page = 0;
    protected int pageSize;

    public PaginatedGui(String title, int size) {
        super(title, size);
        this.pageSize = size - 9; // Deixa a última linha para navegação
    }

    public void setItems(List<T> items) {
        this.items = items;
        this.page = 0;
        updatePage();
    }

    protected void updatePage() {
        inventory.clear();

        if (items == null || items.isEmpty()) return;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            ItemStack icon = createIcon(items.get(i));
            inventory.setItem(i - startIndex, icon);
        }

        // Navegação
        if (page > 0) {
            inventory.setItem(inventory.getSize() - 9, createNavItem("Anterior"));
        }
        if (endIndex < items.size()) {
            inventory.setItem(inventory.getSize() - 1, createNavItem("Próximo"));
        }
    }

    protected abstract ItemStack createIcon(T item);
    protected abstract void onItemClick(T item, Player p);

    private ItemStack createNavItem(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + name);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(true);

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

        int slot = e.getRawSlot();
        if (slot >= inventory.getSize()) return; // Clicou no inventário do jogador

        if (slot == inventory.getSize() - 9 && page > 0) {
            page--;
            updatePage();
            return;
        }

        if (slot == inventory.getSize() - 1 && (page + 1) * pageSize < items.size()) {
            page++;
            updatePage();
            return;
        }

        if (slot < pageSize) {
            int index = (page * pageSize) + slot;
            if (index < items.size()) {
                onItemClick(items.get(index), (Player) e.getWhoClicked());
            }
        }
    }
}
