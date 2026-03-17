package sand.anticheat.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;
import sand.anticheat.util.CheckUtil;

public final class MovementListener implements Listener {

    private final SandAC plugin;
    private final Map<UUID, Location> lastAirPositions = new HashMap<UUID, Location>();
    private final Map<UUID, Integer> stagnantAirTicks = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> baritonePathTicks = new HashMap<UUID, Integer>();
    private final Map<UUID, PendingFallCheck> pendingNoFallChecks = new HashMap<UUID, PendingFallCheck>();

    public MovementListener(SandAC plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                tickAirStuckMonitor();
                tickNoFallMonitor();
            }
        }.runTaskTimer(this.plugin, 20L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = this.plugin.getDataManager().get(player);
        long now = System.currentTimeMillis();
        data.handleMove(player, event.getFrom(), event.getTo(), now);

        if (CheckUtil.isCheckBypass(player)) {
            data.finishVelocityCheck();
            if (player.isOnGround()) {
                data.markSafe(event.getTo());
            }
            return;
        }

        if (CheckUtil.shouldUpdateSafeLocation(player, data, event.getTo())) {
            data.markSafe(event.getTo());
        }

        trackNoFall(player, data, event);

        runVelocity(player, data);
        runCollisionSpeed(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runWaterSpeed(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runSpeed(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runStrafe(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runWallClimb(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runFastClimb(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runHighJump(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runAirJump(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runElytraFlight(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runWaterLeave(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runFlight(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runAirStuck(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runNoClip(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runNoSlow(player, data, event);
        if (event.isCancelled()) {
            return;
        }

        runGuiWalk(player, data, event);
        runBaritoneA(player, data);
    }

    private void runVelocity(Player player, PlayerData data) {
        if (CheckUtil.isVelocityExempt(player, data)) {
            data.finishVelocityCheck();
            data.decayViolation("Combat.Velocity", 0.12D);
            return;
        }

        if (data.getVelocityTicks() < 3) {
            return;
        }

        double responseHorizontal = data.getVelocityResponseHorizontal();
        double responseVertical = data.getVelocityResponseVertical();
        double expectedHorizontal = data.getAppliedVelocityHorizontal();
        double expectedVertical = data.getAppliedVelocityVertical();

        boolean horizontalFailed = expectedHorizontal > 0.22D && responseHorizontal < Math.min(0.10D, expectedHorizontal * 0.18D);
        boolean verticalFailed = expectedVertical > 0.18D && responseVertical < Math.min(0.08D, expectedVertical * 0.35D);

        if (horizontalFailed || verticalFailed) {
            double vl = data.addViolation("Combat.Velocity", horizontalFailed && verticalFailed ? 1.35D : 0.95D);
            this.plugin.getAlertManager().sendViolation(player, "Combat.Velocity", vl,
                    "ratio=" + CheckUtil.format(responseHorizontal) + "/" + CheckUtil.format(expectedHorizontal), false);
        } else {
            data.decayViolation("Combat.Velocity", 0.18D);
        }

        if (data.getVelocityTicks() >= 6) {
            data.finishVelocityCheck();
        }
    }

    private void runCollisionSpeed(Player player, PlayerData data, PlayerMoveEvent event) {
        long sinceVelocity = System.currentTimeMillis() - data.getLastVelocityMillis();
        if (player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isDead()
                || CheckUtil.isInLiquid(player)
                || CheckUtil.isClimbing(player)
                || sinceVelocity < 1200L) {
            data.decayViolation("Movement.CollisionSpeed", 0.16D);
            return;
        }

        LivingEntity target = CheckUtil.findNearbyCombatEntity(player, 2.35D);
        if (target == null || player.isGliding()) {
            data.decayViolation("Movement.CollisionSpeed", 0.12D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        double allowed = (player.isSprinting() ? 0.37D : 0.29D) + CheckUtil.getSpeedPotionBoost(player);
        double alignment = CheckUtil.getHorizontalAlignment(player, target, data);

        boolean suspicious = target.getLocation().distanceSquared(player.getLocation()) <= 3.10D
                && alignment > 0.90D
                && horizontal > Math.max(0.42D, allowed + 0.04D)
                && !CheckUtil.isNearHorizontalCollision(player)
                && !CheckUtil.isOnIce(player);

        if (!suspicious) {
            data.decayViolation("Movement.CollisionSpeed", 0.16D);
            return;
        }

        double vl = data.addViolation("Movement.CollisionSpeed", 1.00D + ((horizontal - allowed) * 8.5D));
        if (vl < 2.80D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.CollisionSpeed", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.CollisionSpeed", vl,
                "move=" + CheckUtil.format(horizontal) + ", align=" + CheckUtil.format(alignment), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.CollisionSpeed", vl);
    }

    private void runWaterSpeed(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        boolean inWater = player.isSwimming() || CheckUtil.isWaterLoggedMovement(player, event.getFrom(), event.getTo());
        if (!inWater
                || player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isDead()
                || CheckUtil.isClimbing(player)
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.WaterSpeed", 0.16D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        double allowed = 0.19D
                + (player.isSwimming() ? 0.05D : 0.0D)
                + (player.isSprinting() ? 0.03D : 0.0D)
                + CheckUtil.getDepthStriderBoost(player)
                + CheckUtil.getDolphinsGraceBoost(player)
                + (CheckUtil.getSpeedPotionBoost(player) * 0.45D);

        Material feet = player.getLocation().getBlock().getType();
        if (feet == Material.BUBBLE_COLUMN) {
            allowed += 0.08D;
        }

        if (horizontal <= allowed + 0.08D) {
            data.decayViolation("Movement.WaterSpeed", 0.14D);
            return;
        }

        double over = horizontal - allowed;
        double vl = data.addViolation("Movement.WaterSpeed", 0.75D + (over * 6.0D));
        if (vl < 4.20D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.WaterSpeed", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.WaterSpeed", vl,
                "move=" + CheckUtil.format(horizontal) + ", max=" + CheckUtil.format(allowed), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.WaterSpeed", vl);
    }

    private void runSpeed(Player player, PlayerData data, PlayerMoveEvent event) {
        if (CheckUtil.isMovementExempt(player, data)) {
            data.decayViolation("Movement.Speed", 0.18D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        if (horizontal <= 0.0D) {
            return;
        }

        double allowed = player.isSneaking() ? 0.18D : (player.isSprinting() ? 0.37D : 0.29D);
        allowed += CheckUtil.getSpeedPotionBoost(player);
        allowed += Math.max(0.0D, (player.getWalkSpeed() - 0.20F) * 1.60D);

        if (!player.isOnGround()) {
            allowed += 0.075D;
            if (player.isSprinting()) {
                allowed += 0.045D;
            }
            if (data.getAirTicks() <= 3) {
                allowed += 0.020D;
            }
            if (data.getDeltaY() > 0.30D) {
                allowed += 0.020D;
            }
        }
        if (CheckUtil.isOnIce(player)) {
            allowed += 0.26D;
        }
        if (CheckUtil.isNearHorizontalCollision(player)) {
            allowed += 0.030D;
        }

        double margin = player.isOnGround() ? 0.085D : 0.090D;
        if (horizontal <= allowed + margin) {
            data.decayViolation("Movement.Speed", 0.15D);
            return;
        }

        double over = horizontal - allowed;
        double vl = data.addViolation("Movement.Speed", 0.55D + (over * 5.0D));
        if (vl < 5.40D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.Speed", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.Speed", vl,
                "move=" + CheckUtil.format(horizontal) + ", max=" + CheckUtil.format(allowed), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.Speed", vl);
    }

    private void runStrafe(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        if (player.isOnGround()
                || data.getAirTicks() < 3
                || player.isGliding()
                || player.isSwimming()
                || CheckUtil.isInLiquid(player)
                || CheckUtil.isClimbing(player)
                || CheckUtil.isOnIce(player)
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.Strafe", 0.16D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        double lastHorizontal = data.getLastHorizontalPerTick();
        float absYaw = Math.abs(data.getDeltaYaw());
        boolean suspicious = player.isSprinting()
                && horizontal > 0.345D
                && lastHorizontal > 0.300D
                && horizontal >= lastHorizontal - 0.004D
                && absYaw > 10.0F;

        if (!suspicious) {
            data.decayViolation("Movement.Strafe", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.Strafe", 0.70D + ((horizontal - 0.33D) * 5.5D));
        if (vl < 4.30D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.Strafe", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.Strafe", vl,
                "move=" + CheckUtil.format(horizontal) + ", keep=" + CheckUtil.format(lastHorizontal), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.Strafe", vl);
    }

    private void runWallClimb(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        if (player.isOnGround()
                || data.getAirTicks() < 6
                || player.isGliding()
                || CheckUtil.isMovementExempt(player, data)
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.WallClimb", 0.16D);
            return;
        }

        boolean suspicious = CheckUtil.isNearHorizontalCollision(player)
                && data.getDeltaY() > 0.08D
                && data.getLastDeltaY() > 0.0D;

        if (!suspicious) {
            data.decayViolation("Movement.WallClimb", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.WallClimb", 0.85D + (data.getDeltaY() * 3.5D));
        if (vl < 3.00D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.WallClimb", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.WallClimb", vl,
                "air=" + data.getAirTicks() + ", y=" + CheckUtil.format(data.getDeltaY()), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.WallClimb", vl);
    }

    private void runFastClimb(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        if (!CheckUtil.isClimbing(player)
                || player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isDead()
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.FastClimb", 0.16D);
            return;
        }

        double allowed = 0.24D + (player.isSprinting() ? 0.02D : 0.0D);
        if (data.getDeltaY() <= allowed) {
            data.decayViolation("Movement.FastClimb", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.FastClimb", 0.90D + ((data.getDeltaY() - allowed) * 8.0D));
        if (vl < 3.20D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.FastClimb", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.FastClimb", vl,
                "climb=" + CheckUtil.format(data.getDeltaY()) + ", max=" + CheckUtil.format(allowed), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.FastClimb", vl);
    }

    private void runHighJump(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        if (player.isOnGround()
                || data.getAirTicks() > 2
                || CheckUtil.isMovementExempt(player, data)
                || CheckUtil.isSoftLanding(event.getFrom())
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.HighJump", 0.16D);
            return;
        }

        double allowed = 0.52D + CheckUtil.getJumpBoost(player);
        double deltaY = data.getDeltaY();

        if (deltaY <= allowed) {
            data.decayViolation("Movement.HighJump", 0.14D);

            if (data.getAirTicks() == 1) {
                long lastJumpTime = data.getCustomLong("highjump_lasttime");
                if (now - lastJumpTime >= 2000L) {
                    data.setCustomCounter("highjump_chain", 0);
                }
            }
            return;
        }

        double excess = deltaY - allowed;
        double baseVL = 1.00D + (excess * 6.0D);

        if (deltaY > 0.80D && data.getAirTicks() <= 2) {
            baseVL += excess * 2.0D;
        }

        double chainMultiplier = 1.0D;
        if (data.getAirTicks() == 1) {
            Integer consecutiveJumps = data.getCustomCounter("highjump_chain");
            if (consecutiveJumps == null) consecutiveJumps = 0;

            long lastJumpTime = data.getCustomLong("highjump_lasttime");
            if (now - lastJumpTime < 2000L) {
                consecutiveJumps++;
                chainMultiplier = 1.0D + (consecutiveJumps * 0.25D);
            } else {
                consecutiveJumps = 1;
            }

            data.setCustomCounter("highjump_chain", consecutiveJumps);
            data.setCustomLong("highjump_lasttime", now);
        }

        double vl = data.addViolation("Movement.HighJump", baseVL * chainMultiplier);

        String details = "jump=" + CheckUtil.format(deltaY) + ", max=" + CheckUtil.format(allowed);
        Integer chainCount = data.getCustomCounter("highjump_chain");
        if (chainCount != null && chainCount > 1) {
            details += ", chain=" + chainCount;
        }
        if (deltaY > 0.80D) {
            details += ", extreme=true";
        }

        if (vl < 3.20D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.HighJump", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.HighJump", vl, details, true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.HighJump", vl);
    }

    private void runAirJump(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        if (player.isOnGround()
                || data.getAirTicks() < 6
                || CheckUtil.isMovementExempt(player, data)
                || CheckUtil.isSolidBelow(player, 1.10D)
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.AirJump", 0.16D);
            return;
        }

        boolean suspicious = data.getDeltaY() > 0.14D && data.getLastDeltaY() < -0.03D;
        if (!suspicious) {
            data.decayViolation("Movement.AirJump", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.AirJump", 0.95D + (data.getDeltaY() * 3.8D));
        if (vl < 3.00D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.AirJump", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.AirJump", vl,
                "air=" + data.getAirTicks() + ", y=" + CheckUtil.format(data.getDeltaY()), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.AirJump", vl);
    }

    private void runElytraFlight(Player player, PlayerData data, PlayerMoveEvent event) {
        if (!player.isGliding()) {
            data.decayViolation("Movement.ElytraFlight", 0.16D);
            return;
        }

        long sinceFirework = System.currentTimeMillis() - data.getLastFireworkBoostMillis();
        double horizontal = data.getHorizontalPerTick();
        double allowed = sinceFirework < 1500L ? 2.80D : 1.15D;
        boolean suspicious = horizontal > allowed || (sinceFirework >= 1500L && data.getDeltaY() > 0.15D);

        if (!suspicious) {
            data.decayViolation("Movement.ElytraFlight", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.ElytraFlight",
                0.85D + Math.max(0.0D, (horizontal - allowed) * 2.8D) + (data.getDeltaY() > 0.15D ? 0.35D : 0.0D));
        if (vl < 3.50D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.ElytraFlight", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.ElytraFlight", vl,
                "move=" + CheckUtil.format(horizontal) + ", rocket=" + (sinceFirework < 1500L), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.ElytraFlight", vl);
    }

    private void runWaterLeave(Player player, PlayerData data, PlayerMoveEvent event) {
        long now = System.currentTimeMillis();
        if (player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isDead()
                || player.isGliding()
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L) {
            data.decayViolation("Movement.WaterLeave", 0.16D);
            return;
        }

        Material belowFrom = event.getFrom().clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();
        Material belowTo = event.getTo().clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();
        boolean specialBase = belowFrom == Material.SOUL_SAND
                || belowTo == Material.SOUL_SAND
                || belowFrom == Material.MAGMA_BLOCK
                || belowTo == Material.MAGMA_BLOCK;

        if (!specialBase || !CheckUtil.isWaterLoggedMovement(player, event.getFrom(), event.getTo()) || data.getDeltaY() <= 0.90D) {
            data.decayViolation("Movement.WaterLeave", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.WaterLeave", 1.10D + ((data.getDeltaY() - 0.90D) * 4.0D));
        if (vl < 3.00D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.WaterLeave", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.WaterLeave", vl,
                "boostY=" + CheckUtil.format(data.getDeltaY()) + ", base=" + belowTo.name(), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.WaterLeave", vl);
    }

    private void runFlight(Player player, PlayerData data, PlayerMoveEvent event) {
        if (CheckUtil.isMovementExempt(player, data)) {
            data.decayViolation("Movement.Flight", 0.20D);
            return;
        }

        if (player.isOnGround() || data.getAirTicks() < 7) {
            data.decayViolation("Movement.Flight", 0.20D);
            return;
        }

        double deltaY = data.getDeltaY();
        boolean hovering = Math.abs(deltaY) < 0.028D;
        boolean fallingTooSlow = deltaY > -0.045D && data.getAirTicks() > 9;

        if (!hovering && !fallingTooSlow) {
            data.decayViolation("Movement.Flight", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.Flight", hovering ? 1.05D : 0.75D);
        if (vl < 4.00D) {
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.Flight", vl,
                "air=" + data.getAirTicks() + ", y=" + CheckUtil.format(deltaY), true);
    }

    private void runAirStuck(Player player, PlayerData data, PlayerMoveEvent event) {
        if (CheckUtil.isMovementExempt(player, data)
                || player.isOnGround()
                || data.getAirTicks() < 8
                || CheckUtil.isSolidBelow(player, 0.75D)) {
            data.decayViolation("Movement.AirStuck", 0.16D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        double deltaY = Math.abs(data.getDeltaY());
        boolean suspicious = horizontal < 0.0035D && deltaY < 0.0025D;

        if (!suspicious) {
            data.decayViolation("Movement.AirStuck", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.AirStuck", 0.95D);
        if (vl < 3.20D) {
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.AirStuck", vl,
                "air=" + data.getAirTicks() + ", move=" + CheckUtil.format(horizontal), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.AirStuck", vl);
    }

    private void runNoSlow(Player player, PlayerData data, PlayerMoveEvent event) {
        if (CheckUtil.isMovementExempt(player, data) || !CheckUtil.isUsingSlowItem(player)) {
            data.decayViolation("Movement.NoSlow", 0.16D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        double allowed = player.isSprinting() ? 0.19D : 0.15D;
        allowed += CheckUtil.getSpeedPotionBoost(player) * 0.50D;
        if (CheckUtil.isOnIce(player)) {
            allowed += 0.12D;
        }

        if (horizontal <= allowed + 0.05D) {
            data.decayViolation("Movement.NoSlow", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.NoSlow", 1.00D + ((horizontal - allowed) * 7.0D));
        if (vl < 3.20D) {
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.NoSlow", vl,
                "slow=" + CheckUtil.format(horizontal) + ", max=" + CheckUtil.format(allowed), true);
    }

    private void runGuiWalk(Player player, PlayerData data, PlayerMoveEvent event) {
        long sinceInteraction = System.currentTimeMillis() - data.getLastInventoryInteractionMillis();
        boolean openContainer = this.plugin.getInventoryListener().hasOpenContainer(player);
        boolean guiContext = openContainer || sinceInteraction < 180L;

        if (!guiContext || CheckUtil.isMovementExempt(player, data)) {
            data.decayViolation("Movement.GuiWalk", 0.16D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        boolean suspicious = (openContainer && horizontal > 0.12D)
                || (!openContainer && sinceInteraction < 180L && horizontal > 0.18D);

        if (!suspicious) {
            data.decayViolation("Movement.GuiWalk", 0.12D);
            return;
        }

        double vl = data.addViolation("Movement.GuiWalk", openContainer ? 0.95D : 0.65D);
        if (vl < 3.20D) {
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.GuiWalk", vl,
                "move=" + CheckUtil.format(horizontal) + ", gui=" + (openContainer ? "container" : "click"), true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.GuiWalk", vl);
    }

    private void runNoClip(Player player, PlayerData data, PlayerMoveEvent event) {
        if (CheckUtil.isMovementExempt(player, data) || !CheckUtil.isInsideSolid(player)) {
            data.decayViolation("Movement.NoClip", 0.16D);
            return;
        }

        double horizontal = data.getHorizontalPerTick();
        boolean suspicious = horizontal > 0.08D || data.getAirTicks() > 3;
        if (!suspicious) {
            data.decayViolation("Movement.NoClip", 0.14D);
            return;
        }

        double vl = data.addViolation("Movement.NoClip", 1.05D + (horizontal * 2.8D));
        if (vl < 3.00D) {
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.NoClip", vl);
            return;
        }

        setback(event, data);
        this.plugin.getAlertManager().sendViolation(player, "Movement.NoClip", vl,
                "move=" + CheckUtil.format(horizontal) + ", solid=true", true);
        this.plugin.getPunishmentManager().considerPunishment(player, "Movement.NoClip", vl);
    }

    private void runBaritoneA(Player player, PlayerData data) {
        UUID uniqueId = player.getUniqueId();
        if (CheckUtil.isMovementExempt(player, data) || data.getHorizontalPerTick() <= 0.16D) {
            decayBaritone(uniqueId, data);
            return;
        }

        boolean axisAligned = Math.abs(data.getDeltaX()) < 0.004D
                || Math.abs(data.getDeltaZ()) < 0.004D
                || Math.abs(Math.abs(data.getDeltaX()) - Math.abs(data.getDeltaZ())) < 0.004D;
        boolean suspicious = axisAligned
                && Math.abs(data.getDeltaYaw()) < 0.01F
                && CheckUtil.getGridYawDistance(player.getLocation().getYaw()) < 0.01F;

        if (!suspicious) {
            decayBaritone(uniqueId, data);
            return;
        }

        int ticks = this.baritonePathTicks.getOrDefault(uniqueId, Integer.valueOf(0)).intValue() + 1;
        this.baritonePathTicks.put(uniqueId, Integer.valueOf(ticks));
        if (ticks < 80 || ticks % 20 != 0) {
            return;
        }

        double vl = data.addViolation("Player.Baritone/A", 0.85D);
        this.plugin.getAlertManager().sendViolation(player, "Player.Baritone/A", vl,
                "path=" + ticks + "t, yaw=" + CheckUtil.format(player.getLocation().getYaw()), false);
        this.plugin.getPunishmentManager().considerPunishment(player, "Player.Baritone/A", vl);
    }

    private void trackNoFall(Player player, PlayerData data, PlayerMoveEvent event) {
        UUID uniqueId = player.getUniqueId();
        if (CheckUtil.isMovementExempt(player, data) || CheckUtil.isSoftLanding(event.getTo())) {
            this.pendingNoFallChecks.remove(uniqueId);
            return;
        }

        if (!player.isOnGround() || data.getGroundTicks() > 1) {
            return;
        }

        double fallDistance = data.getLastAirFallDistance();
        if (fallDistance <= 3.0D) {
            this.pendingNoFallChecks.remove(uniqueId);
            data.decayViolation("Player.NoFall", 0.12D);
            return;
        }

        this.pendingNoFallChecks.put(uniqueId, new PendingFallCheck(System.currentTimeMillis(), fallDistance));
    }

    private void tickAirStuckMonitor() {
        for (UUID uniqueId : this.lastAirPositions.keySet().toArray(new UUID[0])) {
            if (Bukkit.getPlayer(uniqueId) == null) {
                this.lastAirPositions.remove(uniqueId);
                this.stagnantAirTicks.remove(uniqueId);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = this.plugin.getDataManager().get(player);
            UUID uniqueId = player.getUniqueId();
            Location current = player.getLocation().clone();

            if (shouldResetAirMonitor(player, data)) {
                this.lastAirPositions.put(uniqueId, current);
                this.stagnantAirTicks.remove(uniqueId);
                data.decayViolation("Movement.AirStuck", 0.16D);
                continue;
            }

            Location previous = this.lastAirPositions.put(uniqueId, current);
            if (previous == null || !previous.getWorld().equals(current.getWorld())) {
                this.stagnantAirTicks.remove(uniqueId);
                continue;
            }

            double horizontal = Math.hypot(current.getX() - previous.getX(), current.getZ() - previous.getZ());
            double deltaY = Math.abs(current.getY() - previous.getY());
            if (horizontal > 0.012D || deltaY > 0.010D) {
                this.stagnantAirTicks.remove(uniqueId);
                data.decayViolation("Movement.AirStuck", 0.12D);
                continue;
            }

            int ticks = this.stagnantAirTicks.getOrDefault(uniqueId, 0) + 2;
            this.stagnantAirTicks.put(uniqueId, ticks);

            if (ticks < 10) {
                continue;
            }

            double vl = data.addViolation("Movement.AirStuck", 1.05D);
            this.stagnantAirTicks.remove(uniqueId);

            if (vl < 3.00D) {
                this.plugin.getAlertManager().sendViolation(player, "Movement.AirStuck", vl,
                        "hover=" + ticks + ", move=0.00", false);
                continue;
            }

            setback(player, data);
            this.plugin.getAlertManager().sendViolation(player, "Movement.AirStuck", vl,
                    "hover=" + ticks + ", move=0.00", true);
            this.plugin.getPunishmentManager().considerPunishment(player, "Movement.AirStuck", vl);
        }
    }

    private void tickNoFallMonitor() {
        long now = System.currentTimeMillis();
        for (UUID uniqueId : this.pendingNoFallChecks.keySet().toArray(new UUID[0])) {
            PendingFallCheck pending = this.pendingNoFallChecks.get(uniqueId);
            Player player = Bukkit.getPlayer(uniqueId);
            if (pending == null || player == null || !player.isOnline()) {
                this.pendingNoFallChecks.remove(uniqueId);
                continue;
            }

            PlayerData data = this.plugin.getDataManager().get(player);
            if (CheckUtil.isCheckBypass(player)
                    || player.isDead()
                    || CheckUtil.isSoftLanding(player.getLocation())
                    || data.getLastFallDamageMillis() >= pending.createdMillis - 150L) {
                this.pendingNoFallChecks.remove(uniqueId);
                continue;
            }

            if (now - pending.createdMillis < 350L) {
                continue;
            }

            double vl = data.addViolation("Player.NoFall",
                    0.90D + Math.min(1.80D, (pending.fallDistance - 3.0D) * 0.30D));
            this.plugin.getAlertManager().sendViolation(player, "Player.NoFall", vl,
                    "fall=" + CheckUtil.format(pending.fallDistance), false);
            this.plugin.getPunishmentManager().considerPunishment(player, "Player.NoFall", vl);
            this.pendingNoFallChecks.remove(uniqueId);
        }
    }

    private boolean shouldResetAirMonitor(Player player, PlayerData data) {
        return CheckUtil.isCheckBypass(player)
                || CheckUtil.isMovementExempt(player, data)
                || player.isOnGround()
                || player.isDead()
                || player.isInsideVehicle()
                || player.isGliding()
                || data.getAirTicks() < 6
                || CheckUtil.isSolidBelow(player, 1.50D);
    }

    private void setback(PlayerMoveEvent event, PlayerData data) {
        Location safe = data.getLastSafeLocation();
        if (safe == null) {
            event.setCancelled(true);
            return;
        }

        Location corrected = safe.clone();
        corrected.setYaw(event.getTo().getYaw());
        corrected.setPitch(event.getTo().getPitch());
        event.setTo(corrected);
    }

    private void setback(Player player, PlayerData data) {
        Location safe = data.getLastSafeLocation();
        if (safe == null) {
            return;
        }

        Location corrected = safe.clone();
        corrected.setYaw(player.getLocation().getYaw());
        corrected.setPitch(player.getLocation().getPitch());
        player.teleport(corrected);
    }

    private void decayBaritone(UUID uniqueId, PlayerData data) {
        int current = this.baritonePathTicks.getOrDefault(uniqueId, Integer.valueOf(0)).intValue();
        if (current <= 2) {
            this.baritonePathTicks.remove(uniqueId);
        } else {
            this.baritonePathTicks.put(uniqueId, Integer.valueOf(current - 2));
        }
        data.decayViolation("Player.Baritone/A", 0.04D);
    }

    private static final class PendingFallCheck {
        private final long createdMillis;
        private final double fallDistance;

        private PendingFallCheck(long createdMillis, double fallDistance) {
            this.createdMillis = createdMillis;
            this.fallDistance = fallDistance;
        }
    }
}
