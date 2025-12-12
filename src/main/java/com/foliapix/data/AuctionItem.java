package com.foliapix.data;

public class AuctionItem {

    private int id;
    private String vendedor;
    private String item;
    private String itemBase64;
    private double preco;

    // Construtores, Getters e Setters
    public AuctionItem(int id, String vendedor, String item, double preco, String itemBase64) {
        this.id = id;
        this.vendedor = vendedor;
        this.item = item;
        this.preco = preco;
        this.itemBase64 = itemBase64;
    }

    public int getId() { return id; }
    public String getVendedor() { return vendedor; }
    public String getItem() { return item; }
    public String getItemBase64() { return itemBase64; }
    public double getPreco() { return preco; }
}
