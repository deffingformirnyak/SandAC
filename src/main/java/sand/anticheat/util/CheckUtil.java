package sand.anticheat.util;

import java.text.DecimalFormat;
import java.util.Collection;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import sand.anticheat.data.PlayerData;

public final class CheckUtil {

    private static final DecimalFormat DECIMAL = new DecimalFormat("0.00");

    private CheckUtil() {
    }

    public static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    public static boolean isNearShulkerBox(Player player, double radius) {
        Location loc = player.getLocation();
        int minX = (int) Math.floor(loc.getX() - radius);
        int maxX = (int) Math.ceil(loc.getX() + radius);
        int minY = (int) Math.floor(loc.getY() - radius);
        int maxY = (int) Math.ceil(loc.getY() + radius);
        int minZ = (int) Math.floor(loc.getZ() - radius);
        int maxZ = (int) Math.ceil(loc.getZ() + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material type = loc.getWorld().getBlockAt(x, y, z).getType();
                    if (type.name().contains("SHULKER_BOX")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double getAimError(Player attacker, LivingEntity target) {
        Location[] points = new Location[] {
                target.getEyeLocation(),
                getHitboxCenter(target),
                target.getLocation().clone().add(0.0D, target.getHeight() * 0.35D, 0.0D)
        };

        double best = 180.0D;
        for (Location point : points) {
            best = Math.min(best, getAimError(attacker, point));
        }
        return best;
    }

    public static double distanceToHitbox(Location eye, LivingEntity target) {
        BoundingBox box = target.getBoundingBox();
        double x = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return eye.toVector().distance(new Vector(x, y, z));
    }

    public static boolean hasLineOfSight(Player attacker, LivingEntity target) {
        Location eye = attacker.getEyeLocation();
        Location[] points = new Location[] {
                target.getEyeLocation(),
                getHitboxCenter(target),
                target.getLocation().clone().add(0.0D, 0.25D, 0.0D)
        };

        for (Location point : points) {
            Vector ray = point.toVector().subtract(eye.toVector());
            World world = attacker.getWorld();
            RayTraceResult result = world.rayTraceBlocks(eye, ray.normalize(), ray.length(), FluidCollisionMode.NEVER, true);
            if (result == null) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasLineOfSight(Player player, Location point) {
        Location eye = player.getEyeLocation();
        Vector ray = point.toVector().subtract(eye.toVector());
        if (ray.lengthSquared() <= 1.0E-6D) {
            return true;
        }

        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, ray.normalize(), ray.length(), FluidCollisionMode.NEVER, true);
        return result == null;
    }

    public static boolean isDirectlyAiming(Player attacker, LivingEntity target, double range) {
        RayTraceResult result = attacker.getWorld().rayTraceEntities(
                attacker.getEyeLocation(),
                attacker.getEyeLocation().getDirection(),
                range,
                0.15D,
                entity -> entity.getUniqueId().equals(target.getUniqueId())
        );
        return result != null && target.equals(result.getHitEntity());
    }

    public static boolean isCheckBypass(Player player) {
        GameMode gameMode = player.getGameMode();
        return player.isOp()
                || player.hasPermission("sandac.bypass")
                || gameMode == GameMode.CREATIVE
                || gameMode == GameMode.SPECTATOR;
    }

    public static boolean isMovementExempt(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        return player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isDead()
                || player.isGliding()
                || player.isSwimming()
                || isInLiquid(player)
                || isClimbing(player)
                || now - data.getLastVelocityMillis() < 1200L
                || now - data.getLastTeleportMillis() < 1000L;
    }

    public static boolean isVelocityExempt(Player player, PlayerData data) {
        long sinceVelocity = System.currentTimeMillis() - data.getLastVelocityMillis();
        return !data.isAwaitingVelocity()
                || sinceVelocity > 1000L
                || player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isDead()
                || player.isGliding()
                || player.isSwimming()
                || isInLiquid(player)
                || isClimbing(player)
                || isNearHorizontalCollision(player);
    }

    public static boolean shouldUpdateSafeLocation(Player player, PlayerData data, Location location) {
        if (!player.isOnGround()) {
            return false;
        }
        if (isInLiquid(player) || isClimbing(player)) {
            return false;
        }
        if (Math.abs(data.getDeltaY()) > 0.25D || data.getHorizontalPerTick() > 0.45D) {
            return false;
        }

        Block below = location.clone().subtract(0.0D, 0.15D, 0.0D).getBlock();
        return below.getType().isSolid();
    }

    public static boolean isOnIce(Player player) {
        Material type = player.getLocation().clone().subtract(0.0D, 0.2D, 0.0D).getBlock().getType();
        return type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
    }

    public static boolean isClimbing(Player player) {
        Material type = player.getLocation().getBlock().getType();
        return type == Material.LADDER || type == Material.VINE || type == Material.SCAFFOLDING;
    }

    public static boolean isInLiquid(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material head = player.getEyeLocation().getBlock().getType();
        return feet == Material.WATER
                || feet == Material.KELP
                || feet == Material.SEAGRASS
                || feet == Material.TALL_SEAGRASS
                || feet == Material.BUBBLE_COLUMN
                || feet == Material.LAVA
                || head == Material.WATER
                || head == Material.LAVA;
    }

    public static boolean isUsingSlowItem(Player player) {
        if (!player.isHandRaised() && !player.isBlocking()) {
            return false;
        }

        return isSlowItem(player.getInventory().getItemInMainHand()) || isSlowItem(player.getInventory().getItemInOffHand());
    }

    public static double getSpeedPotionBoost(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
        if (effect == null) {
            return 0.0D;
        }

        return 0.057D * (effect.getAmplifier() + 1);
    }

    public static double getJumpBoost(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.JUMP);
        if (effect == null) {
            return 0.0D;
        }

        return 0.10D * (effect.getAmplifier() + 1);
    }

    public static double getDepthStriderBoost(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null) {
            return 0.0D;
        }

        int level = boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
        if (level <= 0) {
            return 0.0D;
        }

        return 0.045D * level;
    }

    public static double getDolphinsGraceBoost(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        if (effect == null) {
            return 0.0D;
        }

        return 0.16D;
    }

    public static LivingEntity findNearbyCombatEntity(Player player, double range) {
        LivingEntity nearest = null;
        double nearestDistance = range * range;

        for (Entity nearby : player.getNearbyEntities(range, range, range)) {
            if (!(nearby instanceof LivingEntity) || nearby instanceof ArmorStand || nearby.equals(player)) {
                continue;
            }
            LivingEntity living = (LivingEntity) nearby;
            if (living.isDead()) {
                continue;
            }

            double distance = living.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = living;
            }
        }

        return nearest;
    }

    public static double getHorizontalAlignment(Player player, LivingEntity target, PlayerData data) {
        Vector move = new Vector(data.getDeltaX(), 0.0D, data.getDeltaZ());
        if (move.lengthSquared() <= 1.0E-4D) {
            return 0.0D;
        }

        Vector towardTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0D);
        if (towardTarget.lengthSquared() <= 1.0E-4D) {
            return 0.0D;
        }

        return move.normalize().dot(towardTarget.normalize());
    }

    public static boolean isNearHorizontalCollision(Player player) {
        Location base = player.getLocation();
        double[][] offsets = new double[][] {
                {0.34D, 0.0D}, {-0.34D, 0.0D}, {0.0D, 0.34D}, {0.0D, -0.34D},
                {0.28D, 0.28D}, {0.28D, -0.28D}, {-0.28D, 0.28D}, {-0.28D, -0.28D}
        };

        for (double[] offset : offsets) {
            if (isSolid(base, offset[0], 0.1D, offset[1]) || isSolid(base, offset[0], 1.0D, offset[1])) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasHotbarPearl(Player player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (itemStack != null && itemStack.getType() == Material.ENDER_PEARL) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWaterLoggedMovement(Player player, Location from, Location to) {
        return isWaterMaterial(player.getLocation().getBlock().getType())
                || isWaterMaterial(player.getEyeLocation().getBlock().getType())
                || isWaterMaterial(from.getBlock().getType())
                || isWaterMaterial(to.getBlock().getType())
                || isWaterMaterial(from.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType())
                || isWaterMaterial(to.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType());
    }

    public static boolean isSolidBelow(Player player, double depth) {
        return player.getLocation().clone().subtract(0.0D, depth, 0.0D).getBlock().getType().isSolid();
    }

    public static boolean isInsideSolid(Player player) {
        BoundingBox box = player.getBoundingBox().expand(-1.0E-3D);
        World world = player.getWorld();

        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX());
        int maxY = (int) Math.floor(box.getMaxY());
        int maxZ = (int) Math.floor(box.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (!type.isOccluding() || block.isPassable()) {
                        continue;
                    }

                    BoundingBox blockBox = new BoundingBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
                    if (box.overlaps(blockBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isSoftLanding(Location location) {
        if (location == null) {
            return false;
        }

        Material feet = location.getBlock().getType();
        Material below = location.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();
        return isSoftLandingMaterial(feet) || isSoftLandingMaterial(below);
    }

    public static float getGridYawDistance(float yaw) {
        float wrapped = wrapDegrees(yaw);
        float nearest = Math.round(wrapped / 45.0F) * 45.0F;
        return Math.abs(wrapDegrees(wrapped - nearest));
    }

    public static double average(Collection<Double> values) {
        double total = 0.0D;
        for (double value : values) {
            total += value;
        }
        return values.isEmpty() ? 0.0D : total / values.size();
    }

    public static double standardDeviation(Collection<Double> values, double average) {
        if (values.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;
        for (double value : values) {
            double diff = value - average;
            total += diff * diff;
        }
        return Math.sqrt(total / values.size());
    }

    public static String format(double value) {
        return DECIMAL.format(value);
    }

    public static double getAimError(Player attacker, Location point) {
        Location eye = attacker.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Vector toTarget = point.toVector().subtract(eye.toVector()).normalize();
        double dot = Math.max(-1.0D, Math.min(1.0D, direction.dot(toTarget)));
        return Math.toDegrees(Math.acos(dot));
    }

    private static boolean isSlowItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        Material type = itemStack.getType();
        return type == Material.SHIELD
                || type == Material.BOW
                || type == Material.CROSSBOW
                || type == Material.TRIDENT
                || type == Material.POTION
                || type == Material.HONEY_BOTTLE
                || type.isEdible();
    }

    private static Location getHitboxCenter(LivingEntity target) {
        BoundingBox box = target.getBoundingBox();
        return new Location(target.getWorld(),
                (box.getMinX() + box.getMaxX()) * 0.5D,
                (box.getMinY() + box.getMaxY()) * 0.5D,
                (box.getMinZ() + box.getMaxZ()) * 0.5D);
    }

    private static boolean isSolid(Location base, double x, double y, double z) {
        return base.clone().add(x, y, z).getBlock().getType().isSolid();
    }

    private static boolean isWaterMaterial(Material material) {
        return material == Material.WATER
                || material == Material.BUBBLE_COLUMN
                || material == Material.KELP
                || material == Material.SEAGRASS
                || material == Material.TALL_SEAGRASS;
    }

    private static boolean isSoftLandingMaterial(Material material) {
        return material == Material.WATER
                || material == Material.BUBBLE_COLUMN
                || material == Material.HAY_BLOCK
                || material == Material.SLIME_BLOCK
                || material == Material.HONEY_BLOCK
                || material == Material.COBWEB
                || material == Material.LADDER
                || material == Material.VINE
                || material == Material.SCAFFOLDING
                || material.name().endsWith("_BED");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
