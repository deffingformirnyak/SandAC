package sand.anticheat.data;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import sand.anticheat.util.CheckUtil;

public final class PlayerData {

    private final UUID uniqueId;
    private final Map<String, Double> violations = new LinkedHashMap<String, Double>();
    private final Deque<Double> aimSamples = new ArrayDeque<Double>();
    private final Deque<Double> yawDeltaSamples = new ArrayDeque<Double>();
    private final Deque<Double> pitchDeltaSamples = new ArrayDeque<Double>();

    private final Deque<Float> rotationDeltas = new LinkedList<>();
    private final Deque<Long> attackTimings = new LinkedList<>();

    private final Map<String, Integer> customCounters = new HashMap<>();
    private final Map<String, Long> customLongs = new HashMap<>();

    private Location lastLocation;
    private Location lastSafeLocation;

    private float deltaYaw;
    private float deltaPitch;
    private float lastDeltaYaw;
    private float lastDeltaPitch;

    private double deltaX;
    private double deltaY;
    private double lastDeltaY;
    private double deltaZ;
    private double lastHorizontalDistance;
    private double lastHorizontalPerTick;
    private double horizontalDistance;
    private double trackedFallDistance;
    private double lastAirFallDistance;

    private long lastMoveMillis;
    private long moveIntervalMillis = 50L;
    private long lastAttackMillis;
    private long lastVelocityMillis;
    private long lastTeleportMillis;
    private long lastPearlInventoryMillis;
    private long lastInventoryInteractionMillis;
    private long lastFireworkBoostMillis;
    private long lastFallDamageMillis;

    private int airTicks;
    private int groundTicks;
    private int sameTargetHits;

    private int lastPearlSlot = -1;
    private boolean hotbarPearlBeforeClick;

    private boolean awaitingVelocity;
    private int velocityTicks;
    private double appliedVelocityHorizontal;
    private double appliedVelocityVertical;
    private double velocityResponseHorizontal;
    private double velocityResponseVertical;
    private Vector appliedVelocity = new Vector();

    private UUID lastTargetId;
    private boolean lastOnGround;

    public PlayerData(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public void handleMove(Player player, Location from, Location to, long now) {
        double previousTickFactor = Math.max(1.0D, this.moveIntervalMillis / 50.0D);
        this.lastHorizontalDistance = this.horizontalDistance;
        this.lastHorizontalPerTick = this.horizontalDistance / previousTickFactor;

        this.deltaX = to.getX() - from.getX();
        this.lastDeltaY = this.deltaY;
        this.deltaY = to.getY() - from.getY();
        this.deltaZ = to.getZ() - from.getZ();
        this.horizontalDistance = Math.hypot(this.deltaX, this.deltaZ);

        this.lastDeltaYaw = this.deltaYaw;
        this.lastDeltaPitch = this.deltaPitch;
        this.deltaYaw = CheckUtil.wrapDegrees(to.getYaw() - from.getYaw());
        this.deltaPitch = to.getPitch() - from.getPitch();

        pushSample(this.yawDeltaSamples, Math.abs(this.deltaYaw), 12);
        pushSample(this.pitchDeltaSamples, Math.abs(this.deltaPitch), 12);

        if (this.lastMoveMillis > 0L) {
            this.moveIntervalMillis = Math.max(1L, now - this.lastMoveMillis);
        }
        this.lastMoveMillis = now;
        this.lastLocation = to.clone();

        boolean onGround = player.isOnGround();
        if (onGround) {
            if (!this.lastOnGround) {
                this.lastAirFallDistance = this.trackedFallDistance;
            }
            this.groundTicks++;
            this.airTicks = 0;
            this.trackedFallDistance = 0.0D;
        } else {
            this.airTicks++;
            this.groundTicks = 0;
            if (this.deltaY < 0.0D) {
                this.trackedFallDistance += -this.deltaY;
            }
        }
        this.lastOnGround = onGround;

        if (this.awaitingVelocity) {
            this.velocityTicks++;
            this.velocityResponseHorizontal = Math.max(this.velocityResponseHorizontal, this.horizontalDistance);
            this.velocityResponseVertical = Math.max(this.velocityResponseVertical, Math.abs(this.deltaY));
        }
    }

    public void markSafe(Location location) {
        if (location != null) {
            this.lastSafeLocation = location.clone();
        }
    }

    public void markAttack(Entity target) {
        this.lastAttackMillis = System.currentTimeMillis();
        UUID targetId = target.getUniqueId();
        if (targetId.equals(this.lastTargetId)) {
            this.sameTargetHits++;
        } else {
            this.lastTargetId = targetId;
            this.sameTargetHits = 1;
            this.aimSamples.clear();
        }
        attackTimings.addLast(System.currentTimeMillis());
        if (attackTimings.size() > 10) {
            attackTimings.removeFirst();
        }
        addRotationDelta(Math.abs(deltaYaw) + Math.abs(deltaPitch));
    }

    public void addAimSample(double aimError) {
        pushSample(this.aimSamples, aimError, 10);
    }

    public Deque<Double> getAimSamples() {
        return this.aimSamples;
    }

    public Deque<Double> getYawDeltaSamples() {
        return this.yawDeltaSamples;
    }

    public Deque<Double> getPitchDeltaSamples() {
        return this.pitchDeltaSamples;
    }

    public void markVelocity(Vector vector) {
        this.lastVelocityMillis = System.currentTimeMillis();
        this.appliedVelocity = vector.clone();
        this.appliedVelocityHorizontal = Math.hypot(vector.getX(), vector.getZ());
        this.appliedVelocityVertical = Math.abs(vector.getY());
        this.velocityResponseHorizontal = 0.0D;
        this.velocityResponseVertical = 0.0D;
        this.velocityTicks = 0;
        this.awaitingVelocity = this.appliedVelocityHorizontal > 0.18D || this.appliedVelocityVertical > 0.16D;
    }

    public Deque<Float> getRotationDeltas() {
        return rotationDeltas;
    }

    public Deque<Long> getAttackTimings() {
        return attackTimings;
    }

    public void addRotationDelta(float delta) {
        rotationDeltas.addLast(delta);
        if (rotationDeltas.size() > 12) {
            rotationDeltas.removeFirst();
        }
    }

    public void finishVelocityCheck() {
        this.awaitingVelocity = false;
        this.velocityTicks = 0;
        this.appliedVelocityHorizontal = 0.0D;
        this.appliedVelocityVertical = 0.0D;
        this.velocityResponseHorizontal = 0.0D;
        this.velocityResponseVertical = 0.0D;
        this.appliedVelocity = new Vector();
    }

    public void markPearlInventoryClick(boolean hotbarPearlBeforeClick, int slot) {
        this.lastPearlInventoryMillis = System.currentTimeMillis();
        this.hotbarPearlBeforeClick = hotbarPearlBeforeClick;
        this.lastPearlSlot = slot;
    }

    public void markInventoryInteraction() {
        this.lastInventoryInteractionMillis = System.currentTimeMillis();
    }

    public void markFireworkBoost() {
        this.lastFireworkBoostMillis = System.currentTimeMillis();
    }

    public void markFallDamage() {
        this.lastFallDamageMillis = System.currentTimeMillis();
    }

    public double addViolation(String key, double amount) {
        double next = getViolation(key) + amount;
        this.violations.put(key, next);
        return next;
    }

    public double decayViolation(String key, double amount) {
        double next = Math.max(0.0D, getViolation(key) - amount);
        this.violations.put(key, next);
        return next;
    }

    public double getViolation(String key) {
        Double value = this.violations.get(key);
        return value == null ? 0.0D : value.doubleValue();
    }

    public Map<String, Double> getViolations() {
        return Collections.unmodifiableMap(this.violations);
    }

    public Integer getCustomCounter(String key) {
        return customCounters.get(key);
    }

    public void setCustomCounter(String key, int value) {
        customCounters.put(key, value);
    }

    public long getCustomLong(String key) {
        return customLongs.getOrDefault(key, 0L);
    }

    public void setCustomLong(String key, long value) {
        customLongs.put(key, value);
    }

    public void resetViolations() {
        this.violations.clear();
        this.aimSamples.clear();
        this.yawDeltaSamples.clear();
        this.pitchDeltaSamples.clear();
        this.sameTargetHits = 0;
        this.lastTargetId = null;
        this.lastPearlInventoryMillis = 0L;
        this.lastPearlSlot = -1;
        this.hotbarPearlBeforeClick = false;
        this.lastInventoryInteractionMillis = 0L;
        this.lastFireworkBoostMillis = 0L;
        this.lastFallDamageMillis = 0L;
        this.trackedFallDistance = 0.0D;
        this.lastAirFallDistance = 0.0D;
        finishVelocityCheck();
    }

    public UUID getUniqueId() {
        return this.uniqueId;
    }

    public Location getLastLocation() {
        return this.lastLocation;
    }

    public Location getLastSafeLocation() {
        return this.lastSafeLocation == null ? null : this.lastSafeLocation.clone();
    }

    public float getDeltaYaw() {
        return this.deltaYaw;
    }

    public float getDeltaPitch() {
        return this.deltaPitch;
    }

    public float getLastDeltaYaw() {
        return this.lastDeltaYaw;
    }

    public float getLastDeltaPitch() {
        return this.lastDeltaPitch;
    }

    public double getDeltaX() {
        return this.deltaX;
    }

    public double getDeltaY() {
        return this.deltaY;
    }

    public double getLastDeltaY() {
        return this.lastDeltaY;
    }

    public double getDeltaZ() {
        return this.deltaZ;
    }

    public double getHorizontalDistance() {
        return this.horizontalDistance;
    }

    public double getLastHorizontalDistance() {
        return this.lastHorizontalDistance;
    }

    public double getHorizontalPerTick() {
        double tickFactor = Math.max(1.0D, this.moveIntervalMillis / 50.0D);
        return this.horizontalDistance / tickFactor;
    }

    public double getLastHorizontalPerTick() {
        return this.lastHorizontalPerTick;
    }

    public long getLastAttackMillis() {
        return this.lastAttackMillis;
    }

    public long getLastVelocityMillis() {
        return this.lastVelocityMillis;
    }

    public void setLastVelocityMillis(long lastVelocityMillis) {
        this.lastVelocityMillis = lastVelocityMillis;
    }

    public long getLastTeleportMillis() {
        return this.lastTeleportMillis;
    }

    public void setLastTeleportMillis(long lastTeleportMillis) {
        this.lastTeleportMillis = lastTeleportMillis;
    }

    public int getAirTicks() {
        return this.airTicks;
    }

    public int getGroundTicks() {
        return this.groundTicks;
    }

    public int getSameTargetHits() {
        return this.sameTargetHits;
    }

    public long getLastPearlInventoryMillis() {
        return this.lastPearlInventoryMillis;
    }

    public long getLastInventoryInteractionMillis() {
        return this.lastInventoryInteractionMillis;
    }

    public long getLastFireworkBoostMillis() {
        return this.lastFireworkBoostMillis;
    }

    public long getLastFallDamageMillis() {
        return this.lastFallDamageMillis;
    }

    public double getLastAirFallDistance() {
        return this.lastAirFallDistance;
    }

    public boolean isHotbarPearlBeforeClick() {
        return this.hotbarPearlBeforeClick;
    }

    public int getLastPearlSlot() {
        return this.lastPearlSlot;
    }

    public boolean isAwaitingVelocity() {
        return this.awaitingVelocity;
    }

    public int getVelocityTicks() {
        return this.velocityTicks;
    }

    public double getAppliedVelocityHorizontal() {
        return this.appliedVelocityHorizontal;
    }

    public double getAppliedVelocityVertical() {
        return this.appliedVelocityVertical;
    }

    public double getVelocityResponseHorizontal() {
        return this.velocityResponseHorizontal;
    }

    public double getVelocityResponseVertical() {
        return this.velocityResponseVertical;
    }

    public Vector getAppliedVelocity() {
        return this.appliedVelocity.clone();
    }

    private void pushSample(Deque<Double> samples, double value, int maxSize) {
        samples.addLast(value);
        while (samples.size() > maxSize) {
            samples.removeFirst();
        }
    }
}
