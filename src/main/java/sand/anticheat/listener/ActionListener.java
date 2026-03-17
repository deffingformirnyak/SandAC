package sand.anticheat.listener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;
import sand.anticheat.util.CheckUtil;

public final class ActionListener implements Listener {

    private static final BlockFace[] ADJACENT_FACES = new BlockFace[] {
            BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };

    private final SandAC plugin;
    private final Map<UUID, Long> bowPullMillis = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> blockPlaceMillis = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, ActionPattern> miningPatterns = new ConcurrentHashMap<UUID, ActionPattern>();

    public ActionListener(SandAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (CheckUtil.isCheckBypass(player)) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Material type = item.getType();
        Action action = event.getAction();
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && (type == Material.BOW || type == Material.CROSSBOW)) {
            this.bowPullMillis.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
        }

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && type == Material.FIREWORK_ROCKET
                && player.isGliding()) {
            this.plugin.getDataManager().get(player).markFireworkBoost();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }

        Player player = (Player) entity;
        if (CheckUtil.isCheckBypass(player) || event.getBow() == null || event.getBow().getType() != Material.BOW) {
            return;
        }

        PlayerData data = this.plugin.getDataManager().get(player);
        long now = System.currentTimeMillis();
        Long startedAt = this.bowPullMillis.get(player.getUniqueId());
        if (startedAt == null) {
            data.decayViolation("Combat.FastBow", 0.10D);
            return;
        }

        long elapsed = now - startedAt.longValue();
        double expectedMillis = Math.max(0.0D, ((Math.sqrt(1.0D + (3.0D * event.getForce())) - 1.0D) * 1000.0D));
        if (elapsed + 120.0D >= expectedMillis || event.getForce() < 0.35F) {
            data.decayViolation("Combat.FastBow", 0.12D);
            return;
        }

        double shortage = expectedMillis - elapsed;
        double vl = data.addViolation("Combat.FastBow", 0.85D + Math.min(1.25D, shortage / 250.0D));
        boolean blocked = vl >= 3.00D;
        if (blocked) {
            event.setCancelled(true);
        }

        this.plugin.getAlertManager().sendViolation(player, "Combat.FastBow", vl,
                "draw=" + elapsed + "ms, need=" + CheckUtil.format(expectedMillis) + "ms",
                blocked);
        this.plugin.getPunishmentManager().considerPunishment(player, "Combat.FastBow", vl);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (CheckUtil.isCheckBypass(player)) {
            return;
        }

        PlayerData data = this.plugin.getDataManager().get(player);
        long now = System.currentTimeMillis();
        runAirPlace(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runScaffold(player, data, event, now);
        this.blockPlaceMillis.put(player.getUniqueId(), Long.valueOf(now));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (CheckUtil.isCheckBypass(player)) {
            return;
        }

        PlayerData data = this.plugin.getDataManager().get(player);
        runFastBreak(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runBaritoneB(player, data, event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        this.plugin.getDataManager().get((Player) event.getEntity()).markFallDamage();
    }

    public void clearState(Player player) {
        if (player == null) {
            return;
        }

        UUID uniqueId = player.getUniqueId();
        this.bowPullMillis.remove(uniqueId);
        this.blockPlaceMillis.remove(uniqueId);
        this.miningPatterns.remove(uniqueId);
    }

    private void runAirPlace(Player player, PlayerData data, BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Block against = event.getBlockAgainst();
        if ((against != null && against.getType().isSolid()) || hasAdjacentSupport(placed)) {
            data.decayViolation("Player.AirPlace", 0.16D);
            return;
        }

        double vl = data.addViolation("Player.AirPlace", 1.10D);
        boolean blocked = vl >= 2.60D;
        if (blocked) {
            event.setCancelled(true);
        }

        this.plugin.getAlertManager().sendViolation(player, "Player.AirPlace", vl,
                "block=" + placed.getType().name(), blocked);
        this.plugin.getPunishmentManager().considerPunishment(player, "Player.AirPlace", vl);
    }

    private void runScaffold(Player player, PlayerData data, BlockPlaceEvent event, long now) {
        Block placed = event.getBlockPlaced();
        Location playerLocation = player.getLocation();
        double horizontal = data.getHorizontalPerTick();
        boolean belowFeet = placed.getY() <= Math.floor(playerLocation.getY() - 0.70D);

        if (!belowFeet || horizontal <= 0.20D) {
            data.decayViolation("Player.Scaffold", 0.16D);
            return;
        }

        long interval = now - this.blockPlaceMillis.getOrDefault(player.getUniqueId(), Long.valueOf(0L)).longValue();
        Location center = center(placed);
        boolean lineOfSight = CheckUtil.hasLineOfSight(player, center);
        int score = 0;
        if (!lineOfSight) {
            score++;
        }
        if (Math.abs(playerLocation.getPitch()) < 65.0F) {
            score++;
        }
        if (interval > 0L && interval < 125L) {
            score++;
        }
        if (playerLocation.distance(center) > 1.35D) {
            score++;
        }
        if (horizontal > 0.28D) {
            score++;
        }

        if (score < 2) {
            data.decayViolation("Player.Scaffold", 0.14D);
            return;
        }

        double vl = data.addViolation("Player.Scaffold",
                0.75D + (score * 0.22D) + Math.min(0.80D, horizontal * 1.30D));
        boolean blocked = vl >= 3.20D;
        if (blocked) {
            event.setCancelled(true);
        }

        this.plugin.getAlertManager().sendViolation(player, "Player.Scaffold", vl,
                "move=" + CheckUtil.format(horizontal) + ", score=" + score,
                blocked);
        this.plugin.getPunishmentManager().considerPunishment(player, "Player.Scaffold", vl);
    }

    private void runFastBreak(Player player, PlayerData data, BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (isTrivialBreakBlock(type)) {
            data.decayViolation("Player.FastBreak", 0.16D);
            return;
        }

        long duration = this.plugin.getPacketCheckManager().getFinishedDigDuration(player, event.getBlock());
        if (duration == Long.MAX_VALUE) {
            double vl = data.addViolation("Player.FastBreak", 1.00D);
            boolean blocked = vl >= 2.80D;
            if (blocked) {
                event.setCancelled(true);
            }

            this.plugin.getAlertManager().sendViolation(player, "Player.FastBreak", vl,
                    "dig=no-start, block=" + type.name(), blocked);
            this.plugin.getPunishmentManager().considerPunishment(player, "Player.FastBreak", vl);
            return;
        }

        if (duration >= 45L) {
            data.decayViolation("Player.FastBreak", 0.14D);
            return;
        }

        double vl = data.addViolation("Player.FastBreak", 0.90D + ((45L - duration) / 18.0D));
        boolean blocked = vl >= 2.80D;
        if (blocked) {
            event.setCancelled(true);
        }

        this.plugin.getAlertManager().sendViolation(player, "Player.FastBreak", vl,
                "dig=" + duration + "ms, block=" + type.name(), blocked);
        this.plugin.getPunishmentManager().considerPunishment(player, "Player.FastBreak", vl);
    }

    private void runBaritoneB(Player player, PlayerData data, Block block) {
        UUID uniqueId = player.getUniqueId();
        ActionPattern pattern = this.miningPatterns.computeIfAbsent(uniqueId, id -> new ActionPattern());
        long now = System.currentTimeMillis();
        long interval = pattern.lastActionMillis == 0L ? -1L : now - pattern.lastActionMillis;
        float gridDistance = CheckUtil.getGridYawDistance(player.getLocation().getYaw());
        double aimError = CheckUtil.getAimError(player, center(block));

        boolean straightLine = pattern.lastBlock == null
                || (pattern.lastBlock.getWorld().equals(block.getWorld())
                && Math.abs(pattern.lastBlock.getY() - block.getY()) <= 1
                && (pattern.lastBlock.getX() == block.getX() || pattern.lastBlock.getZ() == block.getZ()));

        boolean suspicious = gridDistance < 0.03F
                && aimError < 1.10D
                && interval >= 70L
                && interval <= 260L
                && straightLine;

        if (!suspicious) {
            pattern.reset(block, now);
            data.decayViolation("Player.Baritone/B", 0.10D);
            return;
        }

        pattern.pushInterval(interval, block, now);
        if (pattern.streak < 6) {
            data.decayViolation("Player.Baritone/B", 0.05D);
            return;
        }

        double average = CheckUtil.average(pattern.intervalSamples);
        double deviation = CheckUtil.standardDeviation(pattern.intervalSamples, average);
        if (deviation > 18.0D) {
            data.decayViolation("Player.Baritone/B", 0.10D);
            return;
        }

        double vl = data.addViolation("Player.Baritone/B", 0.95D);
        this.plugin.getAlertManager().sendViolation(player, "Player.Baritone/B", vl,
                "dev=" + CheckUtil.format(deviation) + ", aim=" + CheckUtil.format(aimError), false);
        this.plugin.getPunishmentManager().considerPunishment(player, "Player.Baritone/B", vl);
    }

    private boolean hasAdjacentSupport(Block block) {
        for (BlockFace face : ADJACENT_FACES) {
            if (block.getRelative(face).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private Location center(Block block) {
        return new Location(block.getWorld(),
                block.getX() + 0.5D,
                block.getY() + 0.5D,
                block.getZ() + 0.5D);
    }

    private boolean isTrivialBreakBlock(Material material) {
        String name = material.name();
        return !material.isSolid()
                || material == Material.GLASS
                || material == Material.TNT
                || material == Material.CACTUS
                || material == Material.SUGAR_CANE
                || material == Material.BAMBOO
                || material == Material.SNOW
                || material == Material.SNOW_BLOCK
                || material == Material.HAY_BLOCK
                || name.endsWith("_CARPET")
                || name.endsWith("_GLASS")
                || name.endsWith("_PANE")
                || name.endsWith("_LEAVES")
                || name.endsWith("_WOOL");
    }

    private static final class ActionPattern {
        private final Deque<Double> intervalSamples = new ArrayDeque<Double>();
        private Block lastBlock;
        private long lastActionMillis;
        private int streak = 1;

        private void reset(Block block, long now) {
            this.intervalSamples.clear();
            this.lastBlock = block;
            this.lastActionMillis = now;
            this.streak = 1;
        }

        private void pushInterval(long interval, Block block, long now) {
            this.intervalSamples.addLast(Double.valueOf(interval));
            while (this.intervalSamples.size() > 6) {
                this.intervalSamples.removeFirst();
            }
            this.lastBlock = block;
            this.lastActionMillis = now;
            this.streak++;
        }
    }
}
