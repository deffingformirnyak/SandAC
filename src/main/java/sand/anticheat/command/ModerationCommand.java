package sand.anticheat.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import sand.anticheat.SandAC;
import sand.anticheat.util.DurationUtil;

public final class ModerationCommand implements CommandExecutor, TabCompleter {

    private final SandAC plugin;

    public ModerationCommand(SandAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sandac.moderation")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Недостаточно прав.");
            return true;
        }

        String name = command.getName().toLowerCase();
        if (name.equals("ban")) {
            return handleBan(sender, args);
        }
        if (name.equals("unban")) {
            return handleUnban(sender, args);
        }
        if (name.equals("mute")) {
            return handleMute(sender, args);
        }
        if (name.equals("unmute")) {
            return handleUnmute(sender, args);
        }
        return false;
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(prefix() + ChatColor.RED + "Использование: /ban <ник> <время|навсегда> <причина>");
            return true;
        }

        String targetName = resolveTargetName(args[0]);
        TimeSpec spec = parseTime(args[1]);
        if (!spec.valid) {
            sender.sendMessage(prefix() + ChatColor.RED + "Неверный формат времени. Примеры: 30м, 12ч, 7д, 2н, 1мес, 1г, навсегда.");
            return true;
        }

        String reason = join(args, 2);
        Date expiresAt = spec.permanent ? null : new Date(System.currentTimeMillis() + spec.durationMillis);

        Bukkit.getBanList(BanList.Type.NAME).addBan(
                targetName,
                ChatColor.RED + "Вы забанены.\n"
                        + ChatColor.GRAY + "Срок: " + (spec.permanent ? "навсегда" : DurationUtil.formatDuration(spec.durationMillis)) + "\n"
                        + ChatColor.GRAY + "Причина: " + reason,
                expiresAt,
                sender.getName()
        );

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            online.kickPlayer(ChatColor.RED + "Вы забанены.\n"
                    + ChatColor.GRAY + "Срок: " + (spec.permanent ? "навсегда" : DurationUtil.formatDuration(spec.durationMillis)) + "\n"
                    + ChatColor.GRAY + "Причина: " + reason);
        }

        Bukkit.broadcastMessage(prefix() + ChatColor.WHITE + targetName + ChatColor.RED + " был забанен "
                + ChatColor.GRAY + (spec.permanent ? "навсегда" : "на " + DurationUtil.formatDuration(spec.durationMillis))
                + ChatColor.RED + ". Причина: " + ChatColor.WHITE + reason);
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(prefix() + ChatColor.RED + "Использование: /unban <ник>");
            return true;
        }

        String targetName = resolveBanEntry(args[0]);
        if (targetName == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Игрок не найден в бан-листе.");
            return true;
        }

        Bukkit.getBanList(BanList.Type.NAME).pardon(targetName);
        Bukkit.broadcastMessage(prefix() + ChatColor.WHITE + targetName + ChatColor.GREEN + " был разбанен.");
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(prefix() + ChatColor.RED + "Использование: /mute <ник> <время|навсегда> <причина>");
            return true;
        }

        String targetName = resolveTargetName(args[0]);
        TimeSpec spec = parseTime(args[1]);
        if (!spec.valid) {
            sender.sendMessage(prefix() + ChatColor.RED + "Неверный формат времени. Примеры: 30м, 12ч, 7д, 2н, 1мес, 1г, навсегда.");
            return true;
        }

        String reason = join(args, 2);
        Long expiresAt = spec.permanent ? null : Long.valueOf(System.currentTimeMillis() + spec.durationMillis);
        this.plugin.getMuteManager().mute(targetName, sender.getName(), reason, expiresAt);

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            online.sendMessage(prefix() + ChatColor.RED + "Вы получили мут "
                    + ChatColor.GRAY + "(" + (spec.permanent ? "навсегда" : DurationUtil.formatDuration(spec.durationMillis)) + ")"
                    + ChatColor.RED + ". Причина: " + ChatColor.WHITE + reason);
        }

        Bukkit.broadcastMessage(prefix() + ChatColor.WHITE + targetName + ChatColor.RED + " получил мут "
                + ChatColor.GRAY + (spec.permanent ? "навсегда" : "на " + DurationUtil.formatDuration(spec.durationMillis))
                + ChatColor.RED + ". Причина: " + ChatColor.WHITE + reason);
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(prefix() + ChatColor.RED + "Использование: /unmute <ник>");
            return true;
        }

        String targetName = resolveTargetName(args[0]);
        if (!this.plugin.getMuteManager().unmute(targetName)) {
            sender.sendMessage(prefix() + ChatColor.RED + "Игрок не находится в муте.");
            return true;
        }

        Bukkit.broadcastMessage(prefix() + ChatColor.WHITE + targetName + ChatColor.GREEN + " был размучен.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (args.length == 1) {
            List<String> names = new ArrayList<String>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[0]);
        }

        if ((name.equals("ban") || name.equals("mute")) && args.length == 2) {
            List<String> durations = new ArrayList<String>();
            durations.add("30м");
            durations.add("12ч");
            durations.add("1д");
            durations.add("1н");
            durations.add("1мес");
            durations.add("1г");
            durations.add("навсегда");
            return filter(durations, args[1]);
        }

        return Collections.emptyList();
    }

    private String resolveTargetName(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(input);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : input;
    }

    private String resolveBanEntry(String input) {
        for (BanEntry entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
            if (entry.getTarget() != null && entry.getTarget().equalsIgnoreCase(input)) {
                return entry.getTarget();
            }
        }
        return null;
    }

    private TimeSpec parseTime(String input) {
        if (DurationUtil.isPermanent(input)) {
            return new TimeSpec(true, true, -1L);
        }

        long durationMillis = DurationUtil.parseDurationMillis(input);
        return new TimeSpec(durationMillis > 0L, false, durationMillis);
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

    private String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int index = from; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private String prefix() {
        return ChatColor.DARK_GRAY + "["
                + ChatColor.GOLD + "Sand"
                + ChatColor.YELLOW + "AC"
                + ChatColor.DARK_GRAY + "] "
                + ChatColor.GRAY;
    }

    private static final class TimeSpec {
        private final boolean valid;
        private final boolean permanent;
        private final long durationMillis;

        private TimeSpec(boolean valid, boolean permanent, long durationMillis) {
            this.valid = valid;
            this.permanent = permanent;
            this.durationMillis = durationMillis;
        }
    }
}
