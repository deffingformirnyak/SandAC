package sand.anticheat.alert;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sand.anticheat.SandAC;

public final class AlertManager {

    private static final DecimalFormat DECIMAL = new DecimalFormat("0.00");

    private final SandAC plugin;
    private final Set<UUID> alertViewers = new HashSet<UUID>();

    public AlertManager(SandAC plugin) {
        this.plugin = plugin;
    }

    public void handleJoin(Player player) {
        if (player.hasPermission("sandac.alerts")) {
            this.alertViewers.add(player.getUniqueId());
        }
    }

    public boolean toggleAlerts(Player player) {
        UUID uniqueId = player.getUniqueId();
        if (this.alertViewers.contains(uniqueId)) {
            this.alertViewers.remove(uniqueId);
            return false;
        }

        this.alertViewers.add(uniqueId);
        return true;
    }

    public void sendViolation(Player player, String check, double vl, String details, boolean blocked) {
        String lane = check.startsWith("Movement") ? "MOVE" : (check.startsWith("Player") ? "PLAYER" : "COMBAT");
        String state = blocked ? ChatColor.RED + "BLOCK" : ChatColor.GOLD + "WATCH";
        String message = ChatColor.DARK_GRAY + "["
                + ChatColor.GOLD + "Sand"
                + ChatColor.YELLOW + "AC"
                + ChatColor.DARK_GRAY + "] "
                + ChatColor.GRAY + "<"
                + ChatColor.YELLOW + lane
                + ChatColor.GRAY + "> "
                + ChatColor.WHITE + player.getName()
                + ChatColor.DARK_GRAY + " :: "
                + ChatColor.RED + simplify(check)
                + ChatColor.DARK_GRAY + " :: "
                + ChatColor.GRAY + "VL "
                + ChatColor.WHITE + DECIMAL.format(vl)
                + ChatColor.DARK_GRAY + " :: "
                + ChatColor.GRAY + details
                + ChatColor.DARK_GRAY + " :: "
                + state;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (this.alertViewers.contains(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }

        this.plugin.getLogger().info(ChatColor.stripColor(message));
    }

    public void sendLine(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.DARK_GRAY + "["
                + ChatColor.GOLD + "Sand"
                + ChatColor.YELLOW + "AC"
                + ChatColor.DARK_GRAY + "] "
                + ChatColor.GRAY + message);
    }

    private String simplify(String check) {
        return check.replace("Combat.", "C/")
                .replace("Movement.", "M/")
                .replace("Player.", "P/")
                .replace("KillAura/", "KA-");
    }
}
