package sand.anticheat.listener;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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

        runKillAura(attacker, target, data, aimError, hitboxDistance, directAim, event);
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
        LivingEntity target = (LivingEntity) event.getEntity();
        double allowed = target instanceof Player ? 3.20D : 5.00D;

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
                && data.getSameTargetHits() >= 3
                && hitboxDistance > 2.4D
                && aimError > 7.5D;

        if (!suspicious) {
            data.decayViolation("Combat.HitBox", 0.22D);
            return;
        }

        double vl = data.addViolation("Combat.HitBox", 0.55D + Math.min(0.70D, aimError / 20.0D));
        if (vl >= 4.0D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.HitBox", vl,
                    "aim=" + CheckUtil.format(aimError) + ", ray=miss", true);
        }
        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.HitBox", vl);
    }

    private void runKillAura(Player attacker, LivingEntity target, PlayerData data, double aimError, double hitboxDistance, boolean directAim, EntityDamageByEntityEvent event) {
        data.addAimSample(aimError);

        if (data.getSameTargetHits() < 2) {
            data.decayViolation("Combat.KillAura", 0.08D);
            return;
        }

        Deque<Double> aimSamples = data.getAimSamples();
        Deque<Double> yawSamples = data.getYawDeltaSamples();
        Deque<Double> pitchSamples = data.getPitchDeltaSamples();
        Deque<Double> yawAccelSamples = data.getYawAccelSamples();
        Deque<Double> pitchAccelSamples = data.getPitchAccelSamples();
        Deque<Float> rotationDeltas = data.getRotationDeltas();
        Deque<Long> attackTimings = data.getAttackTimings();

        if (aimSamples.size() < 4 || yawSamples.size() < 4 || pitchSamples.size() < 4) {
            return;
        }

        List<Double> yawAbs = toAbsoluteList(yawSamples);
        List<Double> pitchAbs = toAbsoluteList(pitchSamples);

        double aimAverage = CheckUtil.average(aimSamples);
        double aimDeviation = CheckUtil.standardDeviation(aimSamples, aimAverage);

        double yawAverage = average(yawAbs);
        double yawDeviation = standardDeviation(yawAbs, yawAverage);

        double pitchAverage = average(pitchAbs);
        double pitchDeviation = standardDeviation(pitchAbs, pitchAverage);

        double yawAccelAverage = yawAccelSamples.isEmpty() ? 0.0D : CheckUtil.average(yawAccelSamples);
        double pitchAccelAverage = pitchAccelSamples.isEmpty() ? 0.0D : CheckUtil.average(pitchAccelSamples);

        boolean movingCombat = data.getHorizontalPerTick() > 0.05D
                || target.getVelocity().clone().setY(0.0D).length() > 0.06D;

        int yawSignChanges = countSignChanges(yawSamples);
        int pitchSignChanges = countSignChanges(pitchSamples);

        double yawOscillationScore = computeOscillationScore(yawSamples);
        double pitchOscillationScore = computeOscillationScore(pitchSamples);

        double speedVariance = computeSpeedVariance(yawSamples, pitchSamples);
        int speedJumps = countSpeedJumps(yawSamples, pitchSamples);
        int consistentIntervals = countConsistentIntervals(attackTimings);

        double invalidRatio = computeInvalidGcdRatio(rotationDeltas, attacker);
        int invalidGcdCount = countInvalidGcd(rotationDeltas, attacker);
        int significantRotations = countSignificantRotations(rotationDeltas);

        float absYaw = Math.abs(data.getDeltaYaw());
        float absPitch = Math.abs(data.getDeltaPitch());
        float lastAbsYaw = Math.abs(data.getLastDeltaYaw());
        float lastAbsPitch = Math.abs(data.getLastDeltaPitch());
        float accel = Math.abs(absYaw - lastAbsYaw) + Math.abs(absPitch - lastAbsPitch);

        double score = 0.0D;
        int matches = 0;
        String type = "v0";

        boolean v1 = data.getSameTargetHits() >= 2
                && hitboxDistance > 2.0D
                && directAim
                && aimError < 2.4D
                && absYaw > 22.0F
                && accel > 10.0F;

        if (v1) {
            score += 1.30D;
            matches++;
            type = "v1";
        }

        boolean v2 = directAim
                && aimError < 1.9D
                && lastAbsYaw < 4.5F
                && absYaw > 16.0F
                && data.getSameTargetHits() >= 1;

        if (v2) {
            score += 1.35D;
            matches++;
            type = "v2";
        }

        boolean v3 = directAim
                && movingCombat
                && aimError < 2.5D
                && aimAverage < 3.0D
                && aimDeviation < 0.95D
                && yawDeviation > 0.9D
                && yawDeviation < 6.5D
                && pitchAverage < 4.0D;

        if (v3) {
            score += 1.15D;
            matches++;
            type = "v3";
        }

        boolean v4 = (yawSignChanges >= 3 && pitchSignChanges >= 2)
                || (yawOscillationScore > 0.62D && pitchOscillationScore > 0.45D);

        if (v4) {
            score += 1.10D;
            matches++;
            type = "v4";
        }

        boolean v5 = Math.abs(averageSigned(yawSamples)) < 1.6D
                && Math.abs(averageSigned(pitchSamples)) < 1.2D
                && yawSignChanges >= 2
                && pitchSignChanges >= 2;

        if (v5) {
            score += 0.85D;
            matches++;
            type = "v5";
        }

        boolean v6 = significantRotations >= 4
                && invalidRatio > 0.58D
                && invalidGcdCount >= 4;

        if (v6) {
            score += 1.25D;
            matches++;
            type = "v6";
        }

        boolean v7 = speedVariance < 0.35D
                && directAim
                && aimError < 2.2D;

        if (v7) {
            score += 1.00D;
            matches++;
            type = "v7";
        }

        boolean v8 = speedJumps >= 2
                && directAim;

        if (v8) {
            score += 0.95D;
            matches++;
            type = "v8";
        }

        boolean v9 = consistentIntervals >= 2;

        if (v9) {
            score += 0.85D;
            matches++;
            type = "v9";
        }

        boolean v10 = directAim
                && aimError < 2.05D
                && hitboxDistance > 2.0D
                && yawDeviation > 2.0D
                && yawDeviation < 13.0D
                && pitchDeviation > 0.12D
                && pitchDeviation < 3.2D
                && yawOscillationScore > 0.35D
                && pitchOscillationScore > 0.18D
                && yawSignChanges >= 2
                && speedVariance > 2.0D
                && speedVariance < 95.0D;

        if (v10) {
            score += 1.45D;
            matches++;
            type = "v10";
        }

        boolean v11 = directAim
                && aimError < 2.25D
                && yawAverage > 8.0D
                && yawAverage < 75.0D
                && pitchAverage > 0.8D
                && pitchAverage < 10.0D
                && yawOscillationScore > 0.52D
                && pitchOscillationScore > 0.35D
                && yawSignChanges >= 3
                && pitchSignChanges >= 2
                && speedVariance > 6.0D;

        if (v11) {
            score += 1.50D;
            matches++;
            type = "v11";
        }

        boolean v12 = directAim
                && movingCombat
                && aimError < 2.2D
                && aimAverage < 2.8D
                && aimDeviation < 0.9D
                && yawDeviation > 0.7D
                && yawDeviation < 6.0D
                && pitchDeviation > 0.04D
                && pitchDeviation < 1.25D
                && yawAccelAverage > 0.20D
                && yawAccelAverage < 11.0D
                && invalidRatio < 0.62D;

        if (v12) {
            score += 1.55D;
            matches++;
            type = "v12";
        }

        boolean v13 = directAim
                && aimError < 1.95D
                && aimAverage < 2.4D
                && aimDeviation < 0.75D
                && yawAverage > 0.2D
                && yawAverage < 9.0D
                && pitchAverage > 0.1D
                && pitchAverage < 6.0D
                && yawDeviation < 3.0D
                && pitchDeviation < 2.4D
                && yawAccelAverage < 5.5D
                && pitchAccelAverage < 4.5D
                && speedVariance < 24.0D;

        if (v13) {
            score += 1.05D;
            matches++;
            type = "v13";
        }

        boolean v14 = directAim
                && aimError < 1.65D
                && aimAverage < 2.0D
                && aimDeviation < 0.55D
                && yawDeviation < 2.4D
                && pitchDeviation < 1.8D
                && speedVariance < 12.0D
                && invalidRatio > 0.30D
                && consistentIntervals >= 1;

        if (v14) {
            score += 1.20D;
            matches++;
            type = "v14";
        }

        boolean v15 = (accel > 12.0F || absYaw > 30.0F)
                && (invalidRatio > 0.65D || invalidGcdCount >= 3)
                && aimAverage < 18.0D
                && data.getSameTargetHits() >= 1;

        if (v15) {
            score += 1.70D;
            matches++;
            type = "v15";
        }

        boolean v16 = aimAverage > 6.0D
                && aimDeviation < 6.5D
                && yawDeviation > 1.8D
                && yawDeviation < 10.0D
                && speedVariance > 3.0D
                && yawOscillationScore > 0.20D
                && movingCombat;

        if (v16) {
            score += 1.55D;
            matches++;
            type = "v16_AI_Test";
        }

        boolean v17 = (Math.abs(data.getDeltaYaw()) > 100.0F || Math.abs(data.getDeltaPitch()) > 80.0F)
                && aimError < 25.0D
                && significantRotations >= 1;

        if (v17) {
            score += 1.90D;
            matches++;
            type = "v17";
        }

        if (matches >= 2) {
            score += 1.00D;
        }

        if (matches >= 3) {
            score += 1.25D;
        }

        boolean strong = score >= 1.90D || matches >= 2;
        boolean cancel = score >= 2.60D || matches >= 3;
        boolean punishFast = score >= 3.30D || matches >= 4;

        if (!strong) {
            data.decayViolation("Combat.KillAura", 0.10D);
            return;
        }

        double add = score;
        if (punishFast) {
            add += 1.20D;
        }

        double vl = data.addViolation("Combat.KillAura", add);

        if (cancel || vl >= 3.0D) {
            event.setCancelled(true);
            this.plugin.getAlertManager().sendViolation(attacker, "Combat.KillAura", vl,
                    type
                            + ", score=" + CheckUtil.format(score)
                            + ", m=" + matches
                            + ", aim=" + CheckUtil.format(aimAverage)
                            + ", dev=" + CheckUtil.format(aimDeviation)
                            + ", yaw=" + CheckUtil.format(yawDeviation)
                            + ", osc=" + CheckUtil.format(yawOscillationScore)
                            + ", gcd=" + CheckUtil.format(invalidRatio),
                    true);
        }

        this.plugin.getPunishmentManager().considerPunishment(attacker, "Combat.KillAura", vl);
    }

    private List<Double> toAbsoluteList(Deque<Double> values) {
        List<Double> out = new ArrayList<Double>();
        for (Double value : values) {
            out.add(Math.abs(value.doubleValue()));
        }
        return out;
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0D;
        }

        double sum = 0.0D;
        for (Double value : values) {
            sum += value.doubleValue();
        }
        return sum / values.size();
    }

    private double standardDeviation(List<Double> values, double average) {
        if (values.isEmpty()) {
            return 0.0D;
        }

        double variance = 0.0D;
        for (Double value : values) {
            double diff = value.doubleValue() - average;
            variance += diff * diff;
        }
        return Math.sqrt(variance / values.size());
    }

    private double averageSigned(Deque<Double> samples) {
        if (samples.isEmpty()) {
            return 0.0D;
        }

        double sum = 0.0D;
        for (Double sample : samples) {
            sum += sample.doubleValue();
        }
        return sum / samples.size();
    }

    private int countSignChanges(Deque<Double> samples) {
        if (samples.size() < 2) return 0;

        int changes = 0;
        Double[] arr = samples.toArray(new Double[0]);
        for (int i = 1; i < arr.length; i++) {
            if ((arr[i] > 0.0D && arr[i - 1] < 0.0D) || (arr[i] < 0.0D && arr[i - 1] > 0.0D)) {
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

    private int countSpeedJumps(Deque<Double> yawSamples, Deque<Double> pitchSamples) {
        Double[] yawArr = yawSamples.toArray(new Double[0]);
        Double[] pitchArr = pitchSamples.toArray(new Double[0]);
        int len = Math.min(yawArr.length, pitchArr.length);

        int speedJumps = 0;
        Double lastSpeed = null;

        for (int i = 0; i < len; i++) {
            double currentSpeed = Math.hypot(yawArr[i], pitchArr[i]);
            if (lastSpeed != null) {
                double ratio = lastSpeed > 0.01D ? currentSpeed / lastSpeed : 0.0D;
                if (ratio > 2.50D || ratio < 0.35D) {
                    speedJumps++;
                }
            }
            lastSpeed = currentSpeed;
        }

        return speedJumps;
    }

    private int countConsistentIntervals(Deque<Long> attackTimings) {
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

        return consistentIntervals;
    }

    private int countSignificantRotations(Deque<Float> rotationDeltas) {
        int total = 0;
        for (Float delta : rotationDeltas) {
            if (Math.abs(delta.floatValue()) > 0.5F) {
                total++;
            }
        }
        return total;
    }

    private int countInvalidGcd(Deque<Float> rotationDeltas, Player attacker) {
        double expectedGCD = getExpectedGcd(attacker);
        int invalid = 0;

        for (Float delta : rotationDeltas) {
            if (Math.abs(delta.floatValue()) > 0.5F) {
                double remainder = Math.abs(delta.floatValue()) % expectedGCD;
                double normalizedRemainder = Math.min(remainder, expectedGCD - remainder);
                if (normalizedRemainder > expectedGCD * 0.20D && normalizedRemainder < expectedGCD * 0.80D) {
                    invalid++;
                }
            }
        }

        return invalid;
    }

    private double computeInvalidGcdRatio(Deque<Float> rotationDeltas, Player attacker) {
        int significant = countSignificantRotations(rotationDeltas);
        if (significant == 0) {
            return 0.0D;
        }
        return (double) countInvalidGcd(rotationDeltas, attacker) / significant;
    }

    private double getExpectedGcd(Player attacker) {
        float sensitivity = attacker.getInventory().getItemInMainHand().getType().name().contains("SWORD")
                || attacker.getInventory().getItemInMainHand().getType().name().contains("AXE") ? 1.0F : 0.5F;
        return 0.00390625D * sensitivity;
    }

    private double computeSpeedVariance(Deque<Double> yawSamples, Deque<Double> pitchSamples) {
        if (yawSamples.size() < 3 || pitchSamples.size() < 3) return 0.0D;

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