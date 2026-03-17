package sand.anticheat.punish;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import sand.anticheat.SandAC;
import sand.anticheat.util.CheckUtil;

public final class PunishmentManager {

    private final SandAC plugin;
    private final Set<UUID> punishing = new HashSet<UUID>();
    private final Map<UUID, String> pendingChecks = new HashMap<UUID, String>();

    public PunishmentManager(SandAC plugin) {
        this.plugin = plugin;
    }

    public void considerPunishment(Player player, String check, double vl) {
        if (!this.plugin.getConfig().getBoolean("punishment.enabled", true)) {
            return;
        }
        if (this.punishing.contains(player.getUniqueId()) || CheckUtil.isCheckBypass(player)) {
            return;
        }

        double threshold = getThreshold(check);
        if (threshold > 0.0D && vl >= threshold) {
            punish(player, check);
        }
    }

    public void punish(Player player, String check) {
        if (!player.isOnline() || this.punishing.contains(player.getUniqueId()) || CheckUtil.isCheckBypass(player)) {
            return;
        }

        this.punishing.add(player.getUniqueId());
        this.pendingChecks.put(player.getUniqueId(), check);
        this.plugin.getAlertManager().sendViolation(player, check, 999.0D, "autoban sequence", true);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    applyBan(player.getName(), check);
                    cleanup(player.getUniqueId());
                    cancel();
                    return;
                }

                Location location = player.getLocation().clone();
                location.setYaw(location.getYaw() + 32.0F);
                player.teleport(location);
                player.setVelocity(player.getVelocity().setY(0.42D));

                spawnSpiral(location, this.tick);
                if (this.tick % 8 == 0) {
                    spawnFirework(location);
                }

                if (this.tick >= 32) {
                    applyBan(player.getName(), check);
                    String kickMsg = ChatColor.translateAlternateColorCodes('&', 
                        plugin.getConfig().getString("punishment.kick_message", "&cSandAC\n&7Detected: {check}\n&6Ban: 14 days")
                        .replace("{check}", check));
                    player.kickPlayer(kickMsg);
                    cleanup(player.getUniqueId());
                    cancel();
                    return;
                }

                this.tick += 2;
            }
        }.runTaskTimer(this.plugin, 0L, 2L);
    }

    public void handleQuit(Player player) {
        if (!this.punishing.contains(player.getUniqueId())) {
            return;
        }

        String check = this.pendingChecks.get(player.getUniqueId());
        applyBan(player.getName(), check == null ? "SandAC" : check);
        cleanup(player.getUniqueId());
    }

    private double getThreshold(String check) {
        if ("Combat.BaitBot".equals(check)) return 2.60D;
        if ("Combat.KillAura/Track".equals(check) || "Combat.KillAura/Snap".equals(check)) return 4.20D;
        if ("Combat.HitBox".equals(check)) return 4.00D;
        if ("Combat.Reach".equals(check) || "Combat.Wall".equals(check)) return 5.50D;
        if ("Combat.FastBow".equals(check)) return 4.20D;
        if ("Movement.CollisionSpeed".equals(check)) return 4.50D;
        if ("Movement.Speed".equals(check)) return 6.50D;
        if ("Movement.WaterSpeed".equals(check)) return 5.40D;
        if ("Movement.GuiWalk".equals(check)) return 4.80D;
        if ("Movement.Strafe".equals(check)) return 5.10D;
        if ("Movement.AirStuck".equals(check)) return 4.00D;
        if ("Movement.WaterLeave".equals(check)) return 4.20D;
        if ("Movement.WallClimb".equals(check) || "Movement.FastClimb".equals(check)) return 4.40D;
        if ("Movement.HighJump".equals(check) || "Movement.AirJump".equals(check)) return 4.20D;
        if ("Movement.ElytraFlight".equals(check)) return 5.20D;
        if ("Movement.NoClip".equals(check)) return 3.80D;
        if ("Movement.Timer".equals(check)) return 6.20D;
        if ("Player.NoFall".equals(check)) return 4.20D;
        if ("Player.AirPlace".equals(check) || "Player.FastBreak".equals(check)) return 3.80D;
        if ("Player.Scaffold".equals(check)) return 4.40D;
        if (check.startsWith("Player.BadPackets/")) return 3.60D;
        if (check.startsWith("Player.Baritone/")) return 4.80D;
        return -1.0D;
    }

    private void spawnSpiral(Location base, int tick) {
        double radius = 1.0D;
        for (int point = 0; point < 8; point++) {
            double angle = (tick * 0.28D) + (point * (Math.PI / 4.0D));
            double x = Math.cos(angle) * radius;
            double y = 0.35D + (point * 0.12D);
            double z = Math.sin(angle) * radius;
            base.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, base.clone().add(x, y, z), 2, 0.0D, 0.0D, 0.0D, 0.02D);
            base.getWorld().spawnParticle(Particle.REDSTONE, base.clone().add(-x, y + 0.1D, -z), 1,
                    new Particle.DustOptions(point % 2 == 0 ? Color.RED : Color.ORANGE, 1.6F));
        }
    }

    private void spawnFirework(Location location) {
        Firework firework = location.getWorld().spawn(location.clone().add(0.0D, 0.5D, 0.0D), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(0);
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.RED, Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).trail(true).flicker(true).build());
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(this.plugin, firework::detonate, 1L);
    }

    private void applyBan(String playerName, String check) {
        String reason = ChatColor.translateAlternateColorCodes('&', 
            this.plugin.getConfig().getString("punishment.reason", "SandAC detected cheating ({check})")
            .replace("{check}", check));
        
        Object durationObj = this.plugin.getConfig().get("punishment.duration", 1209600000);
        Date expiresAt = null;
        
        if (durationObj instanceof Number) {
            expiresAt = new Date(System.currentTimeMillis() + ((Number) durationObj).longValue());
        } else if (durationObj instanceof String && ((String) durationObj).equalsIgnoreCase("permanent")) {
            expiresAt = null;
        } else {
            expiresAt = new Date(System.currentTimeMillis() + 1209600000L);
        }

        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expiresAt, "SandAC");
    }

    private void cleanup(UUID uniqueId) {
        this.punishing.remove(uniqueId);
        this.pendingChecks.remove(uniqueId);
    }
}
