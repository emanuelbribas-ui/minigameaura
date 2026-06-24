package com.serpens.checkin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;

public class Checkin extends JavaPlugin implements CommandExecutor, Listener {
    private Connection conn;
    private File configFile;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        getLogger().info("Plugin MinigameAura ativo!");
        try {
            File pasta = new File("minigameaura");
            if (!pasta.exists()) pasta.mkdirs();

            configFile = new File(pasta, "config.yml");
            if (!configFile.exists()) {
                saveResource("config.yml", false);
            }
            config = YamlConfiguration.loadConfiguration(configFile);

            File dbFile = new File(pasta, "database.sqlite");
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS checkins (uuid VARCHAR(36) PRIMARY KEY, data VARCHAR(10), streak INTEGER)");
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.getCommand("checkin").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            abrirGUI(p);
        }
        return true;
    }

    private void abrirGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "check-in");
        int segundosJogados = p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20; 
        
        boolean pegou = false;
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT data FROM checkins WHERE uuid = ?");
            ps.setString(1, p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                pegou = rs.getString("data").equals(LocalDate.now().toString());
            }
        } catch (Exception e) { e.printStackTrace(); }

        ItemStack item;
        if (segundosJogados < 300) {
            item = criarItem(Material.RED_STAINED_GLASS_PANE, "§cEspere 5 minutos no servidor pra poder sacar sua recompensa");
        } else if (pegou) {
            item = criarItem(Material.YELLOW_STAINED_GLASS_PANE, "§eVocê já resgatou sua recompensa");
        } else {
            item = criarItem(Material.LIME_STAINED_GLASS_PANE, "§aclique pra sacar sua recompensa!");
        }

        inv.setItem(4, item);
        p.openInventory(inv);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent e) {
        if (e.getView().getTitle().equals("check-in")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.LIME_STAINED_GLASS_PANE) {
                return;
            }

            Player p = (Player) e.getWhoClicked();
            String uuid = p.getUniqueId().toString();
            int streak = 0;

            try {
                PreparedStatement ps = conn.prepareStatement("SELECT streak FROM checkins WHERE uuid = ?");
                ps.setString(1, uuid);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    streak = rs.getInt("streak");
                }

                int proximoDia = streak + 1;
                if (proximoDia > 31) proximoDia = 1;

                String recompensa = config.getString("dia_" + proximoDia);
                if (recompensa != null && !recompensa.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + p.getName() + " " + recompensa);
                }

                PreparedStatement upsert = conn.prepareStatement(
                    "INSERT OR REPLACE INTO checkins (uuid, data, streak) VALUES (?, ?, ?)"
                );
                upsert.setString(1, uuid);
                upsert.setString(2, LocalDate.now().toString());
                upsert.setInt(3, proximoDia);
                upsert.executeUpdate();

                p.closeInventory();
                p.sendMessage("§aRecompensa do dia " + proximoDia + " resgatada com sucesso!");
                abrirGUI(p);

            } catch (Exception ex) {
                ex.printStackTrace();
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

    @Override
    public void onDisable() {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception e) { e.printStackTrace(); }
    }
}
