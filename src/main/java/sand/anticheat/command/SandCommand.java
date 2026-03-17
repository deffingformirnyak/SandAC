package sand.anticheat.command;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;

public final class SandCommand implements CommandExecutor, TabCompleter {

    private static final DecimalFormat DECIMAL = new DecimalFormat("0.00");

    private final SandAC plugin;

    public SandCommand(SandAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("alerts")) {
            if (!(sender instanceof Player)) {
                this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "Only players can toggle alerts.");
                return true;
            }

            boolean enabled = this.plugin.getAlertManager().toggleAlerts((Player) sender);
            this.plugin.getAlertManager().sendLine(sender, "Alerts " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.GRAY + ".");
            return true;
        }

        if (!sender.hasPermission("sandac.command")) {
            this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("checks")) {
            this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "Combat" + ChatColor.GRAY + ": KillAura/Snap, KillAura/Track, HitBox, BaitBot, Wall, Reach, Velocity, ClickPearl");
            this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "Combat+" + ChatColor.GRAY + ": FastBow");
            this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "Movement" + ChatColor.GRAY + ": CollisionSpeed, Speed, Strafe, WaterSpeed, WaterLeave, GuiWalk, AirStuck, Flight, NoSlow, WallClimb, FastClimb, Timer, HighJump, AirJump, ElytraFlight, NoClip");
            this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "Player" + ChatColor.GRAY + ": NoFall, AirPlace, Scaffold, FastBreak, BadPackets/A-G, Baritone/A-B");
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            if (args.length < 2) {
                this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "Usage: /sandac info <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "Player not found.");
                return true;
            }

            PlayerData data = this.plugin.getDataManager().get(target);
            this.plugin.getAlertManager().sendLine(sender, ChatColor.WHITE + target.getName() + ChatColor.GRAY + " profile");
            if (data.getViolations().isEmpty()) {
                this.plugin.getAlertManager().sendLine(sender, "No active violations.");
                return true;
            }

            for (Map.Entry<String, Double> entry : data.getViolations().entrySet()) {
                if (entry.getValue() <= 0.0D) {
                    continue;
                }

                this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + entry.getKey() + ChatColor.DARK_GRAY + " -> " + ChatColor.WHITE + DECIMAL.format(entry.getValue()));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("sandac.admin")) {
                this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "Usage: /sandac reset <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                this.plugin.getAlertManager().sendLine(sender, ChatColor.RED + "Player not found.");
                return true;
            }

            this.plugin.getDataManager().get(target).resetViolations();
            this.plugin.getAlertManager().sendLine(sender, "Violations reset for " + ChatColor.YELLOW + target.getName() + ChatColor.GRAY + ".");
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "/smd alerts" + ChatColor.GRAY + " - toggle live alerts");
        this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "/sandac checks" + ChatColor.GRAY + " - list loaded checks");
        this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "/sandac info <player>" + ChatColor.GRAY + " - inspect violations");
        this.plugin.getAlertManager().sendLine(sender, ChatColor.YELLOW + "/sandac reset <player>" + ChatColor.GRAY + " - clear violations");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<String>();
            base.add("alerts");
            base.add("checks");
            base.add("help");
            base.add("info");
            base.add("reset");
            return filter(base, args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("reset"))) {
            List<String> names = new ArrayList<String>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> source, String input) {
        List<String> result = new ArrayList<String>();
        for (String entry : source) {
            if (entry.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(entry);
            }
        }
        return result;
    }
}
