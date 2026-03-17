package sand.anticheat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;

public final class PlayerStateListener implements Listener {

    private final SandAC plugin;

    public PlayerStateListener(SandAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = this.plugin.getDataManager().get(event.getPlayer());
        data.markSafe(event.getPlayer().getLocation());
        data.setLastTeleportMillis(System.currentTimeMillis());
        this.plugin.getAlertManager().handleJoin(event.getPlayer());
        this.plugin.getPacketCheckManager().inject(event.getPlayer());
        this.plugin.getBaitBotManager().inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getPunishmentManager().handleQuit(event.getPlayer());
        this.plugin.getBaitBotManager().despawn(event.getPlayer());
        this.plugin.getBaitBotManager().uninject(event.getPlayer());
        this.plugin.getPacketCheckManager().uninject(event.getPlayer());
        this.plugin.getPacketCheckManager().clearState(event.getPlayer());
        this.plugin.getInventoryListener().clearState(event.getPlayer());
        this.plugin.getActionListener().clearState(event.getPlayer());
        this.plugin.getDataManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        this.plugin.getDataManager().get(event.getPlayer()).markVelocity(event.getVelocity());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = this.plugin.getDataManager().get(event.getPlayer());
        data.setLastTeleportMillis(System.currentTimeMillis());
        data.markSafe(event.getTo());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = this.plugin.getDataManager().get(event.getPlayer());
        data.setLastTeleportMillis(System.currentTimeMillis());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        this.plugin.getBaitBotManager().despawn(event.getPlayer());
        this.plugin.getDataManager().get(event.getPlayer()).resetViolations();
        this.plugin.getPacketCheckManager().clearState(event.getPlayer());
        this.plugin.getInventoryListener().clearState(event.getPlayer());
        this.plugin.getActionListener().clearState(event.getPlayer());
    }
}
