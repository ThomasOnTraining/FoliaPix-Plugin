package com.foliapix.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

public class QRCodeRenderer {

    public static ItemStack createMapFromBase64(String base64Image) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            
            // Redimensionar para 128x128 (tamanho do mapa)
            BufferedImage resized = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            resized.getGraphics().drawImage(image, 0, 0, 128, 128, null);

            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            
            MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            view.getRenderers().clear();
            
            view.addRenderer(new MapRenderer() {
                @Override
                public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
                    mapCanvas.drawImage(0, 0, resized);
                }
            });
            
            meta.setMapView(view);
            meta.setDisplayName("§aQR Code Pix");
            mapItem.setItemMeta(meta);
            
            return mapItem;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
