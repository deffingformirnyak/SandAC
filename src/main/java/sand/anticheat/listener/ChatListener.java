package sand.anticheat.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import sand.anticheat.SandAC;
import sand.anticheat.moderation.MuteManager;
import sand.anticheat.util.DurationUtil;

public final class ChatListener implements Listener {

    private final SandAC plugin;

    public ChatListener(SandAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        MuteManager.MuteEntry muteEntry = this.plugin.getMuteManager().getMute(event.getPlayer().getName());
        if (muteEntry == null) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            String remaining = muteEntry.getExpiresAt() == null
                    ? "навсегда"
                    : DurationUtil.formatDuration(Math.max(0L, muteEntry.getExpiresAt().longValue() - System.currentTimeMillis()));
            event.getPlayer().sendMessage(ChatColor.DARK_GRAY + "["
                    + ChatColor.GOLD + "Sand"
                    + ChatColor.YELLOW + "AC"
                    + ChatColor.DARK_GRAY + "] "
                    + ChatColor.RED + "У вас мут " + ChatColor.GRAY + "(" + remaining + ")" + ChatColor.RED + ". "
                    + ChatColor.GRAY + "Причина: " + ChatColor.WHITE + muteEntry.getReason());
        });
    }
}
