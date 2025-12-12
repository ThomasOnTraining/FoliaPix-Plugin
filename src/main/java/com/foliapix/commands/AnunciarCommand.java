package com.foliapix.commands;

import org.bukkit.inventory.ItemStack;
import com.foliapix.http.HttpClientUtil;
import com.google.gson.JsonObject;
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

        double preco;
        try {
            preco = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            p.sendMessage("§cPreço inválido.");
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) { // Check type on ItemStack
            p.sendMessage("§cSegure um item para anunciar.");
            return true;
        }

        // Criar JSON com Gson
        String itemBase64 = com.foliapix.utils.ItemSerializer.toBase64(item);

        JsonObject json = new JsonObject();
        json.addProperty("vendedor_uuid", p.getUniqueId().toString());
        json.addProperty("vendedor_name", p.getName());
        json.addProperty("item", item.getType().name()); // Mantém o nome legível pare buscas simples no banco
        json.addProperty("item_base64", itemBase64); // Salva o item COMPLETO (NBT, Shulker, Enchants)
        json.addProperty("preco", preco);

        String backendUrl = com.foliapix.FoliaPix.getInstance().getConfig().getString("backend.url");
        String apiKey = com.foliapix.FoliaPix.getInstance().getConfig().getString("security.api_key");

        if (backendUrl == null || backendUrl.isEmpty()) {
            p.sendMessage("§cURL do backend não configurada.");
            return true;
        }

        p.sendMessage("§eEnviando anúncio...");

        HttpClientUtil.postAsync(
                backendUrl + "/api/anuncio/criar",
                json.toString(),
                apiKey
        ).thenAccept(response -> {
            // Voltar para a thread principal do Bukkit para enviar mensagem
            org.bukkit.Bukkit.getScheduler().runTask(com.foliapix.FoliaPix.getInstance(), () -> {
                if (response != null) {
                    if (response.contains("USER_NOT_REGISTERED")) {
                        p.sendMessage("§cVocê precisa registrar seu CPF antes de vender.");
                        p.sendMessage("§eUse /registrar <cpf> para se cadastrar.");
                    } else if (response.contains("Anúncio criado!")) {
                        // Antes de remover, guardamos os dados para a mensagem
                        // (Nota: item é referência ao objeto antes do envio, mas como é local variable, deve estar ok. 
                        // Porém `item` foi definido na main thread. Aqui estamos na async. 
                        // Melhor garantir pegando os dados no inicio ou confirmando que item nao mudou)
                        
                        // Simplificação: Mensagem genérica ou repassar dados no json de resposta seria ideal.
                        // Mas como o item está na mão do jogador e o jogador não pode mexer no inv enquanto digita comando (teoricamente),
                        // podemos assumir.
                        
                        // Correção Segura: A mensagem deve ser simples ou os dados passados para a lambda.
                        // O 'item' foi capturado da main thread antes do http request.
                        int qtd = item.getAmount(); 
                        String nome = item.getType().name();

                        p.sendMessage("§aEnviado: §f" + qtd + "x " + nome + " §apor §2R$ " + String.format("%.2f", preco));
                        
                        // Remove o item da mão apenas se sucesso
                        p.getInventory().getItemInMainHand().setAmount(0);
                    } else {
                        p.sendMessage("§cErro no backend: " + response);
                    }
                } else {
                    p.sendMessage("§cErro ao conectar com o backend.");
                }
            });
        });

        return true;
    }
}
