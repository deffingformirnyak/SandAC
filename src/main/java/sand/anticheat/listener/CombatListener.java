package sand.anticheat.listener;

import java.util.Deque;
import java.util.LinkedList;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;
import sand.anticheat.util.CheckUtil;

public final class CombatListener implements Listener {

    private final SandAC plugin;

    public CombatListener(SandAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        if (attacker.equals(target) || CheckUtil.isCheckBypass(attacker)) {
            return;
        }

        PlayerData data = this.plugin.getDataManager().get(attacker);
        data.markAttack(target);
        this.plugin.getBaitBotManager().arm(attacker);

        double hitboxDistance = CheckUtil.distanceToHitbox(attacker.getEyeLocation(), target);
        double aimError = CheckUtil.getAimError(attacker, target);
        boolean directAim = CheckUtil.isDirectlyAiming(attacker, target, 4.6D);

        runWall(attacker, data, hitboxDistance, event);
        if (event.isCancelled()) return;

        runReach(attacker, data, hitboxDistance, event);
        if (event.isCancelled()) return;

        runHitBox(attacker, data, aimError, hitboxDistance, directAim, event);
        if (event.isCancelled()) return;

        runKillAuraSnap(attacker, data, aimError, hitboxDistance, directAim, event);
        if (event.isCancelled()) return;

        runKillAuraTrack(attacker, target, data, aimError, directAim, event);
        if (event.isCancelled()) return;

        runKillAuraOscillation(attacker, data, event);
        if (event.isCancelled()) return;

        runKillAuraGCD(attacker, data, event);
        if (event.isCancelled()) return;

        runKillAuraPattern(attacker, data, aimError, directAim, event);
    }

    private void runWall(Player attacker, PlayerData data, double hitboxDistance, EntityDamageByEntityEvent event) {
        LivingEntity target = (LivingEntity) event.getEntity();
        if (CheckUtil.hasLineOfSight(attacker, target) || hitboxDistance <= 2.15D) {
            data.decayViolation("Combat.Wall", 0.30D);
            return;
        }

        double vl = data.addViolation("Combat.Wall", 2.40D);
        event.setCancelled(true);
        this.plugin.getAlertManager().sendViolation(attacker, "Combat.Wall", vl,
                "dist=" + CheckUtil.format(hitboxDistance) + ", sight=blocked", true);
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.Wall", vl);
    }

    private void runReach(Player attacker, PlayerData data, double hitboxDistance, EntityDamageByEntityEvent event) {
        double allowed = 3.20D;

        if (hitboxDistance <= allowed) {
            data.decayViolation("Combat.Reach", 0.22D);
            return;
        }

        double vl = data.addViolation("Combat.Reach", 0.80D + ((hitboxDistance - allowed) * 6.0D));
        if (vl >= 2.50D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.Reach", vl,
                    "reach=" + CheckUtil.format(hitboxDistance) + ", max=" + CheckUtil.format(allowed), true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.Reach", vl);
    }

    private void runHitBox(Player attacker, PlayerData data, double aimError, double hitboxDistance, boolean directAim, EntityDamageByEntityEvent event) {
        boolean suspicious = !directAim
                && data.getSameTargetHits() >= 2
                && hitboxDistance > 2.0D
                && aimError > 4.20D;

        if (!suspicious) {
            data.decayViolation("Combat.HitBox", 0.18D);
            return;
        }

        double vl = data.addViolation("Combat.HitBox", 0.95D + Math.min(1.00D, aimError / 10.0D));
        if (vl >= 2.40D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.HitBox", vl,
                    "aim=" + CheckUtil.format(aimError) + ", ray=miss", true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.HitBox", vl);
    }

    private void runKillAuraSnap(Player attacker, PlayerData data, double aimError, double hitboxDistance, boolean directAim, EntityDamageByEntityEvent event) {
        float absYaw = Math.abs(data.getDeltaYaw());
        float absPitch = Math.abs(data.getDeltaPitch());
        float lastAbsYaw = Math.abs(data.getLastDeltaYaw());
        float lastAbsPitch = Math.abs(data.getLastDeltaPitch());
        float accel = Math.abs(absYaw - lastAbsYaw) + Math.abs(absPitch - lastAbsPitch);

        boolean snapPattern = data.getSameTargetHits() >= 2
                && hitboxDistance > 2.00D
                && directAim
                && aimError < 2.30D
                && absYaw > 28.0F
                && accel > 16.0F;

        boolean funTimeSnap = directAim
                && aimError < 1.80D
                && lastAbsYaw < 5.0F
                && absYaw > 20.0F
                && data.getSameTargetHits() >= 1;

        if (!snapPattern && !funTimeSnap) {
            data.decayViolation("Combat.KillAura/Snap", 0.20D);
            return;
        }

        double vl = data.addViolation("Combat.KillAura/Snap", funTimeSnap ? 1.25D : 1.05D);
        if (vl >= 3.00D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.KillAura/Snap", vl,
                    "aim=" + CheckUtil.format(aimError) + ", snap=" + CheckUtil.format(absYaw) + ", accel=" + CheckUtil.format(accel), true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.KillAura/Snap", vl);
    }

    private void runKillAuraTrack(Player attacker, LivingEntity target, PlayerData data, double aimError, boolean directAim, EntityDamageByEntityEvent event) {
        data.addAimSample(aimError);

        if (data.getSameTargetHits() < 4) {
            data.decayViolation("Combat.KillAura/Track", 0.12D);
            return;
        }

        Deque<Double> aimSamples = data.getAimSamples();
        Deque<Double> yawSamples = data.getYawDeltaSamples();
        Deque<Double> pitchSamples = data.getPitchDeltaSamples();
        if (aimSamples.size() < 5 || yawSamples.size() < 5 || pitchSamples.size() < 5) {
            return;
        }

        double aimAverage = CheckUtil.average(aimSamples);
        double aimDeviation = CheckUtil.standardDeviation(aimSamples, aimAverage);
        double yawAverage = CheckUtil.average(yawSamples);
        double yawDeviation = CheckUtil.standardDeviation(yawSamples, yawAverage);
        double pitchAverage = CheckUtil.average(pitchSamples);
        double pitchDeviation = CheckUtil.standardDeviation(pitchSamples, pitchAverage);

        boolean movingCombat = data.getHorizontalPerTick() > 0.08D || target.getVelocity().clone().setY(0.0D).length() > 0.10D;

        boolean wellMinePattern = directAim
                && movingCombat
                && aimError < 2.20D
                && aimAverage < 2.50D
                && aimDeviation < 0.70D
                && yawDeviation > 1.50D
                && yawDeviation < 6.00D;

        boolean matrixPattern = directAim
                && movingCombat
                && aimError < 2.65D
                && aimAverage < 3.00D
                && aimDeviation < 0.90D
                && yawAverage > 0.90D
                && yawDeviation < 4.60D
                && pitchAverage < 3.40D;

        if (!wellMinePattern && !matrixPattern) {
            data.decayViolation("Combat.KillAura/Track", 0.15D);
            return;
        }

        double vl = data.addViolation("Combat.KillAura/Track", wellMinePattern ? 1.15D : 1.00D);
        if (vl >= 2.80D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.KillAura/Track", vl,
                    "aim=" + CheckUtil.format(aimAverage) + ", dev=" + CheckUtil.format(aimDeviation) + ", yaw-dev=" + CheckUtil.format(yawDeviation), true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.KillAura/Track", vl);
    }

    private void runKillAuraOscillation(Player attacker, PlayerData data, EntityDamageByEntityEvent event) {
        if (data.getSameTargetHits() < 3) {
            data.decayViolation("Combat.KillAura/Oscillation", 0.14D);
            return;
        }

        Deque<Double> yawSamples = data.getYawDeltaSamples();
        Deque<Double> pitchSamples = data.getPitchDeltaSamples();
        if (yawSamples.size() < 6 || pitchSamples.size() < 6) {
            return;
        }

        int yawSignChanges = countSignChanges(yawSamples);
        int pitchSignChanges = countSignChanges(pitchSamples);

        double yawOscillationScore = computeOscillationScore(yawSamples);
        double pitchOscillationScore = computeOscillationScore(pitchSamples);

        boolean sinCosPattern = (yawSignChanges >= 4 && pitchSignChanges >= 3)
                || (yawOscillationScore > 0.70D && pitchOscillationScore > 0.55D);

        double yawAvg = CheckUtil.average(yawSamples);
        double pitchAvg = CheckUtil.average(pitchSamples);
        boolean timedOscillation = Math.abs(yawAvg) < 1.50D
                && Math.abs(pitchAvg) < 1.00D
                && yawSignChanges >= 3
                && pitchSignChanges >= 2;

        if (!sinCosPattern && !timedOscillation) {
            data.decayViolation("Combat.KillAura/Oscillation", 0.12D);
            return;
        }

        double vl = data.addViolation("Combat.KillAura/Oscillation", 1.10D);
        if (vl >= 3.20D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.KillAura/Oscillation", vl,
                    "yaw-osc=" + CheckUtil.format(yawOscillationScore) + ", pitch-osc=" + CheckUtil.format(pitchOscillationScore) + ", signs=" + yawSignChanges, true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.KillAura/Oscillation", vl);
    }

    private void runKillAuraGCD(Player attacker, PlayerData data, EntityDamageByEntityEvent event) {
        if (data.getSameTargetHits() < 4) {
            data.decayViolation("Combat.KillAura/GCD", 0.14D);
            return;
        }

        Deque<Float> rotationDeltas = data.getRotationDeltas();
        if (rotationDeltas.size() < 8) {
            return;
        }

        float sensitivity = attacker.getInventory().getItemInMainHand().getType().name().contains("SWORD")
                || attacker.getInventory().getItemInMainHand().getType().name().contains("AXE") ? 1.0F : 0.5F;

        double expectedGCD = 0.00390625D * sensitivity;

        int invalidGcdCount = 0;
        int validLargeRotations = 0;
        int totalSignificantRotations = 0;

        for (Float delta : rotationDeltas) {
            if (Math.abs(delta) > 0.5F) {
                totalSignificantRotations++;
                double remainder = Math.abs(delta) % expectedGCD;
                double normalizedRemainder = Math.min(remainder, expectedGCD - remainder);

                if (normalizedRemainder > expectedGCD * 0.20D && normalizedRemainder < expectedGCD * 0.80D) {
                    invalidGcdCount++;
                } else if (Math.abs(delta) > 5.0F) {
                    validLargeRotations++;
                }
            }
        }

        if (totalSignificantRotations < 5) {
            data.decayViolation("Combat.KillAura/GCD", 0.12D);
            return;
        }

        double invalidRatio = (double) invalidGcdCount / totalSignificantRotations;
        double validLargeRatio = (double) validLargeRotations / totalSignificantRotations;

        boolean legitimatePattern = validLargeRatio > 0.30D || totalSignificantRotations < 6;
        boolean gcdBypass = !legitimatePattern && invalidRatio > 0.65D && invalidGcdCount >= 6;

        if (!gcdBypass) {
            data.decayViolation("Combat.KillAura/GCD", 0.12D);
            return;
        }

        double vl = data.addViolation("Combat.KillAura/GCD", 0.85D + (invalidGcdCount * 0.12D));
        if (vl >= 4.20D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.KillAura/GCD", vl,
                    "ratio=" + CheckUtil.format(invalidRatio) + ", invalid=" + invalidGcdCount + "/" + totalSignificantRotations, true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.KillAura/GCD", vl);
    }

    private void runKillAuraPattern(Player attacker, PlayerData data, double aimError, boolean directAim, EntityDamageByEntityEvent event) {
        if (data.getSameTargetHits() < 5) {
            data.decayViolation("Combat.KillAura/Pattern", 0.14D);
            return;
        }

        Deque<Double> yawSamples = data.getYawDeltaSamples();
        Deque<Double> pitchSamples = data.getPitchDeltaSamples();
        Deque<Long> attackTimings = data.getAttackTimings();

        if (yawSamples.size() < 6 || attackTimings.size() < 4) {
            return;
        }

        double speedVariance = computeSpeedVariance(yawSamples, pitchSamples);

        int speedJumps = 0;
        Double lastSpeed = null;
        for (int i = 0; i < Math.min(yawSamples.size(), pitchSamples.size()); i++) {
            Double[] yawArr = yawSamples.toArray(new Double[0]);
            Double[] pitchArr = pitchSamples.toArray(new Double[0]);
            double currentSpeed = Math.hypot(yawArr[i], pitchArr[i]);
            if (lastSpeed != null) {
                double ratio = lastSpeed > 0.01D ? currentSpeed / lastSpeed : 0.0D;
                if (ratio > 2.50D || ratio < 0.35D) {
                    speedJumps++;
                }
            }
            lastSpeed = currentSpeed;
        }

        boolean lerpPattern = speedVariance < 0.25D && directAim && aimError < 2.00D;
        boolean snapSpeedPattern = speedJumps >= 3 && directAim;

        Long[] timings = attackTimings.toArray(new Long[0]);
        int consistentIntervals = 0;
        if (timings.length >= 4) {
            long lastInterval = 0L;
            for (int i = 1; i < timings.length; i++) {
                long interval = timings[i] - timings[i - 1];
                if (lastInterval > 0L && Math.abs(interval - lastInterval) < 25L) {
                    consistentIntervals++;
                }
                lastInterval = interval;
            }
        }
        boolean timerPattern = consistentIntervals >= 2;

        if (!lerpPattern && !snapSpeedPattern && !timerPattern) {
            data.decayViolation("Combat.KillAura/Pattern", 0.12D);
            return;
        }

        double vl = data.addViolation("Combat.KillAura/Pattern",
                (lerpPattern ? 0.85D : 0.0D) + (snapSpeedPattern ? 0.90D : 0.0D) + (timerPattern ? 0.70D : 0.0D));

        if (vl >= 3.00D) {
            event.setCancelled(true);
            String type = lerpPattern ? "lerp" : (snapSpeedPattern ? "snap" : "timer");
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.KillAura/Pattern", vl,
                    "type=" + type + ", var=" + CheckUtil.format(speedVariance) + ", jumps=" + speedJumps, true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.KillAura/Pattern", vl);
    }

    private int countSignChanges(Deque<Double> samples) {
        if (samples.size() < 2) return 0;

        int changes = 0;
        Double[] arr = samples.toArray(new Double[0]);
        for (int i = 1; i < arr.length; i++) {
            if ((arr[i] > 0 && arr[i - 1] < 0) || (arr[i] < 0 && arr[i - 1] > 0)) {
                changes++;
            }
        }
        return changes;
    }

    private double computeOscillationScore(Deque<Double> samples) {
        if (samples.size() < 4) return 0.0D;

        Double[] arr = samples.toArray(new Double[0]);
        int oscillations = 0;

        for (int i = 2; i < arr.length; i++) {
            boolean peak = arr[i - 1] > arr[i - 2] && arr[i - 1] > arr[i];
            boolean valley = arr[i - 1] < arr[i - 2] && arr[i - 1] < arr[i];
            if (peak || valley) {
                oscillations++;
            }
        }

        return (double) oscillations / (arr.length - 2);
    }

    private double computeGCD(Deque<Float> deltas) {
        double result = 0.0D;
        for (Float delta : deltas) {
            if (Math.abs(delta) > 0.001F) {
                result = result == 0.0D ? Math.abs(delta) : gcd(result, Math.abs(delta));
            }
        }
        return result;
    }

    private double gcd(double a, double b) {
        if (b < 0.001D) return a;
        return gcd(b, a % b);
    }

    private double computeSpeedVariance(Deque<Double> yawSamples, Deque<Double> pitchSamples) {
        if (yawSamples.size() < 3) return 0.0D;

        Double[] yaw = yawSamples.toArray(new Double[0]);
        Double[] pitch = pitchSamples.toArray(new Double[0]);
        int len = Math.min(yaw.length, pitch.length);

        double[] speeds = new double[len];
        double sum = 0.0D;
        for (int i = 0; i < len; i++) {
            speeds[i] = Math.hypot(yaw[i], pitch[i]);
            sum += speeds[i];
        }

        double mean = sum / len;
        double variance = 0.0D;
        for (double speed : speeds) {
            variance += (speed - mean) * (speed - mean);
        }

        return variance / len;
    }
}