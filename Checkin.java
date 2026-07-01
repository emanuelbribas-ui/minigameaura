package com.serpens.checkin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Checkin extends JavaPlugin implements CommandExecutor, Listener {

    private Connection conn;
    private File pastaPlugin;
    private FileConfiguration config;
    private final String tituloMenu = "§8§l» §5§lTerminal de Recompensas §8§l«";

    @Override
    public void onEnable() {
        try {
            pastaPlugin = new File(getServer().getWorldContainer(), "minigameaura");
            if (!pastaPlugin.exists()) {
                pastaPlugin.mkdirs();
            }

            File configFile = new File(pastaPlugin, "config.yml");
            if (!configFile.exists()) {
                try {
                    configFile.createNewFile();
                } catch (IOException e) {
                    getLogger().severe("Erro ao gerar config.yml");
                }
            }
            config = YamlConfiguration.loadConfiguration(configFile);
            carregarConfigPadrao();

            conectarBancoDados();

        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (this.getCommand("checkin") != null) {
            this.getCommand("checkin").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void carregarConfigPadrao() {
        if (!config.contains("dia_1")) {
            config.set("dia_1", "apple 5");
            config.set("dia_31", "netherite_block 1");
            try {
                config.save(new File(pastaPlugin, "config.yml"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void conectarBancoDados() {
        try {
            if (conn != null && !conn.isClosed()) {
                return;
            }

            File dbFile = new File(pastaPlugin, "database.sqlite");
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS checkins (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "data VARCHAR(10), " +
                        "streak INTEGER" +
                        ")");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("checkin")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                abrirMenuCheckin(p);
            } else {
                sender.sendMessage("§cApenas jogadores podem executar o /checkin");
            }
            return true;
        }
        return false;
    }

    private void abrirMenuCheckin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, tituloMenu);

        ItemStack painelPreto = criarItem(Material.BLACK_STAINED_GLASS_PANE, "§7");
        ItemStack painelRoxo = criarItem(Material.PURPLE_STAINED_GLASS_PANE, "§7");
        ItemStack painelMagenta = criarItem(Material.MAGENTA_STAINED_GLASS_PANE, "§7");

        int[] bordasPretas = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int slot : bordasPretas) {
            inv.setItem(slot, painelPreto);
        }

        int[] cantosRoxos = {10, 16, 37, 43};
        for (int slot : cantosRoxos) {
            inv.setItem(slot, painelRoxo);
        }

        int[] detalhesMagentas = {11, 15, 19, 25, 28, 34, 38, 42};
        for (int slot : detalhesMagentas) {
            inv.setItem(slot, painelMagenta);
        }

        int segundosJogados = 300;
        try {
            segundosJogados = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;
        } catch (Throwable t) {
        }

        conectarBancoDados();

        boolean jaResgatouHoje = false;
        int streakAtual = 0;

        if (conn != null) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT data, streak FROM checkins WHERE uuid = ?")) {
                ps.setString(1, p.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String ultimaData = rs.getString("data");
                        streakAtual = rs.getInt("streak");
                        jaResgatouHoje = (ultimaData != null && ultimaData.equals(LocalDate.now().toString()));
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            p.sendMessage("§cO banco de dados está inacessível.");
            return;
        }

        int proximoDia = streakAtual + 1;
        if (proximoDia > 31) proximoDia = 1;

        ItemStack cabecaJogador = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta metaCabeca = (SkullMeta) cabecaJogador.getItemMeta();
        if (metaCabeca != null) {
            metaCabeca.setOwningPlayer(p);
            metaCabeca.setDisplayName("§d§lSua Conta, " + p.getName());
            List<String> loreCabeca = new ArrayList<>();
            loreCabeca.add("§7");
            loreCabeca.add("§5» §fDias Seguidos: §d" + streakAtual);
            loreCabeca.add("§5» §fTempo na Sessão: §d" + (segundosJogados / 60) + "m");
            loreCabeca.add("§7");
            metaCabeca.setLore(loreCabeca);
            cabecaJogador.setItemMeta(metaCabeca);
        }
        inv.setItem(13, cabecaJogador);

        ItemStack itemCentral;
        if (segundosJogados < 300) {
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§cVocê precisa jogar 5 minutos.");
            lore.add("§cVolte daqui a pouco!");
            lore.add("§7");
            itemCentral = criarItemComLore(Material.RED_DYE, "§c§lAcesso Negado", lore);
        } else if (jaResgatouHoje) {
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§7Você já obteve sua recompensa diária.");
            lore.add("§7Próximo prêmio será do §ddia " + proximoDia + "§7.");
            lore.add("§7");
            itemCentral = criarItemComLore(Material.YELLOW_DYE, "§e§lJá Resgatado", lore);
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§aClique para ativar o bônus!");
            lore.add("§fPrêmio: §aDia " + proximoDia);
            lore.add("§7");
            itemCentral = criarItemComLore(Material.LIME_DYE, "§a§lDesbloquear Recompensa", lore);
        }

        inv.setItem(31, itemCentral);
        p.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarNoInventario(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(tituloMenu)) {
            e.setCancelled(true); 

            ItemStack itemClicado = e.getCurrentItem();
            if (itemClicado == null) return;

            if (itemClicado.getType() == Material.RED_DYE || itemClicado.getType() == Material.YELLOW_DYE) {
                Player p = (Player) e.getWhoClicked();
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (itemClicado.getType() != Material.LIME_DYE) return;

            Player p = (Player) e.getWhoClicked();
            String uuid = p.getUniqueId().toString();
            int streakAtual = 0;

            conectarBancoDados();
            if (conn == null) {
                p.sendMessage("§cTerminal offline.");
                return;
            }

            try {
                try (PreparedStatement ps = conn.prepareStatement("SELECT streak FROM checkins WHERE uuid = ?")) {
                    ps.setString(1, uuid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            streakAtual = rs.getInt("streak");
                        }
                    }
                }

                int novoDia = streakAtual + 1;
                if (novoDia > 31) novoDia = 1;

                String comandoRecompensa = config.getString("dia_" + novoDia);
                if (comandoRecompensa != null && !comandoRecompensa.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + p.getName() + " " + comandoRecompensa);
                }

                try (PreparedStatement upsert = conn.prepareStatement(
                        "INSERT OR REPLACE INTO checkins (uuid, data, streak) VALUES (?, ?, ?)"
                )) {
                    upsert.setString(1, uuid);
                    upsert.setString(2, LocalDate.now().toString());
                    upsert.setInt(3, novoDia);
                    upsert.executeUpdate();
                }

                p.closeInventory();
                p.sendMessage(" ");
                p.sendMessage("§5§l[Terminal] §fRecompensa do §d§lDia " + novoDia + " §fativada com sucesso!");
                p.sendMessage("§5§l[Terminal] §fOfensiva atualizada: §d" + novoDia + " dias§f.");
                p.sendMessage(" ");
                
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private ItemStack criarItem(Material mat, String nome) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nome);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack criarItemComLore(Material mat, String nome, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nome);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onDisable() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
