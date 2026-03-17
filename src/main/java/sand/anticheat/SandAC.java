package sand.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import sand.anticheat.alert.AlertManager;
import sand.anticheat.bot.BaitBotManager;
import sand.anticheat.command.ModerationCommand;
import sand.anticheat.command.SandCommand;
import sand.anticheat.data.DataManager;
import sand.anticheat.listener.ActionListener;
import sand.anticheat.listener.ChatListener;
import sand.anticheat.listener.CombatListener;
import sand.anticheat.listener.InventoryListener;
import sand.anticheat.listener.MovementListener;
import sand.anticheat.listener.PlayerStateListener;
import sand.anticheat.moderation.MuteManager;
import sand.anticheat.packet.PacketCheckManager;
import sand.anticheat.punish.PunishmentManager;

public final class SandAC extends JavaPlugin {

    private DataManager dataManager;
    private AlertManager alertManager;
    private PunishmentManager punishmentManager;
    private BaitBotManager baitBotManager;
    private InventoryListener inventoryListener;
    private ActionListener actionListener;
    private MuteManager muteManager;
    private PacketCheckManager packetCheckManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.dataManager = new DataManager();
        this.alertManager = new AlertManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.muteManager = new MuteManager(this);
        this.packetCheckManager = new PacketCheckManager(this);
        this.inventoryListener = new InventoryListener(this);
        this.actionListener = new ActionListener(this);
        this.baitBotManager = new BaitBotManager(this);

        Bukkit.getPluginManager().registerEvents(new PlayerStateListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MovementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.inventoryListener, this);
        Bukkit.getPluginManager().registerEvents(this.actionListener, this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        SandCommand sandCommand = new SandCommand(this);
        if (getCommand("sandac") != null) {
            getCommand("sandac").setExecutor(sandCommand);
            getCommand("sandac").setTabCompleter(sandCommand);
        }

        ModerationCommand moderationCommand = new ModerationCommand(this);
        registerModerationCommand("ban", moderationCommand);
        registerModerationCommand("unban", moderationCommand);
        registerModerationCommand("mute", moderationCommand);
        registerModerationCommand("unmute", moderationCommand);

        getLogger().info("SandAC loaded");
    }

    @Override
    public void onDisable() {
        if (this.dataManager != null) {
            this.dataManager.clear();
        }
        if (this.baitBotManager != null) {
            this.baitBotManager.clearAll();
        }
        if (this.packetCheckManager != null) {
            this.packetCheckManager.clearAll();
        }
    }

    private void registerModerationCommand(String name, ModerationCommand moderationCommand) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(moderationCommand);
            getCommand(name).setTabCompleter(moderationCommand);
        }
    }

    public DataManager getDataManager() {
        return this.dataManager;
    }

    public AlertManager getAlertManager() {
        return this.alertManager;
    }

    public PunishmentManager getPunishmentManager() {
        return this.punishmentManager;
    }

    public BaitBotManager getBaitBotManager() {
        return this.baitBotManager;
    }

    public InventoryListener getInventoryListener() {
        return this.inventoryListener;
    }

    public ActionListener getActionListener() {
        return this.actionListener;
    }

    public MuteManager getMuteManager() {
        return this.muteManager;
    }

    public PacketCheckManager getPacketCheckManager() {
        return this.packetCheckManager;
    }
}
