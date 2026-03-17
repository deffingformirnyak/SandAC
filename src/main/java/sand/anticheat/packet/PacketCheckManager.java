package sand.anticheat.packet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInArmAnimation;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockPlace;
import net.minecraft.server.v1_16_R3.PacketPlayInEntityAction;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;
import net.minecraft.server.v1_16_R3.PacketPlayInHeldItemSlot;
import net.minecraft.server.v1_16_R3.PacketPlayInUseItem;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;
import sand.anticheat.util.CheckUtil;

public final class PacketCheckManager {

    private final SandAC plugin;
    private final Map<UUID, PacketState> states = new ConcurrentHashMap<UUID, PacketState>();

    public PacketCheckManager(SandAC plugin) {
        this.plugin = plugin;
        for (Player online : Bukkit.getOnlinePlayers()) {
            inject(online);
        }
    }

    public void inject(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        String handlerName = handlerName(player);
        if (channel.pipeline().get(handlerName) != null) {
            return;
        }

        this.states.computeIfAbsent(player.getUniqueId(), id -> new PacketState());
        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(handlerName) != null) {
                return;
            }

            channel.pipeline().addBefore("packet_handler", handlerName, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    handlePacket(player.getUniqueId(), msg);
                    super.channelRead(ctx, msg);
                }
            });
        });
    }

    public void uninject(Player player) {
        if (player == null) {
            return;
        }

        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        String handlerName = handlerName(player);
        if (channel.pipeline().get(handlerName) == null) {
            return;
        }

        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        });
    }

    public void clearState(Player player) {
        if (player != null) {
            this.states.remove(player.getUniqueId());
        }
    }

    public void clearAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            uninject(online);
        }
        this.states.clear();
    }

    public long getFinishedDigDuration(Player player, Block block) {
        if (player == null || block == null) {
            return Long.MAX_VALUE;
        }

        PacketState state = this.states.get(player.getUniqueId());
        if (state == null || state.lastFinishedDigPos == null) {
            return Long.MAX_VALUE;
        }

        if (!sameBlock(state.lastFinishedDigPos, block) || System.currentTimeMillis() - state.lastFinishedDigMillis > 1200L) {
            return Long.MAX_VALUE;
        }

        return state.lastFinishedDigDurationMillis;
    }

    private void handlePacket(UUID uniqueId, Object msg) {
        PacketState state = this.states.computeIfAbsent(uniqueId, id -> new PacketState());
        long now = System.currentTimeMillis();

        if (msg instanceof PacketPlayInFlying) {
            handleFlying(uniqueId, state, (PacketPlayInFlying) msg, now);
            return;
        }
        if (msg instanceof PacketPlayInHeldItemSlot) {
            handleHeldSlot(uniqueId, state, (PacketPlayInHeldItemSlot) msg, now);
            return;
        }
        if (msg instanceof PacketPlayInEntityAction) {
            handleEntityAction(uniqueId, state, (PacketPlayInEntityAction) msg, now);
            return;
        }
        if (msg instanceof PacketPlayInArmAnimation) {
            handleArmAnimation(uniqueId, state, now);
            return;
        }
        if (msg instanceof PacketPlayInBlockDig) {
            handleBlockDig(uniqueId, state, (PacketPlayInBlockDig) msg, now);
            return;
        }
        if (msg instanceof PacketPlayInBlockPlace || msg instanceof PacketPlayInUseItem) {
            state.lastUseItemMillis = now;
        }
    }

    private void handleFlying(UUID uniqueId, PacketState state, PacketPlayInFlying packet, long now) {
        if (packet.hasLook) {
            state.lastYaw = packet.yaw;
            state.lastPitch = packet.pitch;

            if (packet.pitch > 90.1F || packet.pitch < -90.1F) {
                flag(uniqueId, state, "Player.BadPackets/A", 1.35D,
                        "pitch=" + CheckUtil.format(packet.pitch), false, true, "bad-pitch", 500L, now);
            }
        }

        if (packet.hasPos) {
            double x = packet.x;
            double y = packet.y;
            double z = packet.z;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 3.0E7D || Math.abs(y) > 3.0E7D || Math.abs(z) > 3.0E7D) {
                flag(uniqueId, state, "Player.BadPackets/B", 1.65D,
                        "xyz=" + CheckUtil.format(x) + "/" + CheckUtil.format(y) + "/" + CheckUtil.format(z),
                        false, true, "bad-pos", 500L, now);
            }

            if (state.hasPacketPosition) {
                double dx = x - state.lastX;
                double dz = z - state.lastZ;
                double horizontal = Math.hypot(dx, dz);
                float yaw = packet.hasLook ? packet.yaw : state.lastYaw;
                boolean axisAligned = Math.abs(dx) < 0.004D
                        || Math.abs(dz) < 0.004D
                        || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.004D;

                if (horizontal > 0.12D && axisAligned && CheckUtil.getGridYawDistance(yaw) < 0.03F) {
                    state.gridMoveTicks++;
                } else if (state.gridMoveTicks > 0) {
                    state.gridMoveTicks--;
                }
            }

            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            state.hasPacketPosition = true;
        }

        if (state.flyingWindowStartMillis == 0L) {
            state.flyingWindowStartMillis = now;
        } else if (now - state.flyingWindowStartMillis >= 1000L) {
            evaluateTimer(uniqueId, state, now);
            state.flyingWindowStartMillis = now;
            state.flyingPackets = 0;
        }

        state.flyingPackets++;
    }

    private void evaluateTimer(UUID uniqueId, PacketState state, long now) {
        if (state.flyingPackets >= 32) {
            state.timerBuffer += 3;
        } else if (state.flyingPackets >= 28) {
            state.timerBuffer += 2;
        } else if (state.flyingPackets >= 25) {
            state.timerBuffer += 1;
        } else {
            state.timerBuffer = Math.max(0, state.timerBuffer - 1);
        }

        if (state.timerBuffer < 4) {
            return;
        }

        flag(uniqueId, state, "Movement.Timer",
                0.85D + Math.min(1.35D, (state.flyingPackets - 24) * 0.10D),
                "pps=" + state.flyingPackets,
                false, true, "timer", 1400L, now);
    }

    private void handleHeldSlot(UUID uniqueId, PacketState state, PacketPlayInHeldItemSlot packet, long now) {
        int slot = packet.getItemInHandIndex();
        if (slot < 0 || slot > 8) {
            flag(uniqueId, state, "Player.BadPackets/C", 1.45D,
                    "slot=" + slot, false, true, "bad-slot", 500L, now);
        }
    }

    private void handleEntityAction(UUID uniqueId, PacketState state, PacketPlayInEntityAction packet, long now) {
        PacketPlayInEntityAction.EnumPlayerAction action = packet.c();
        if (action == PacketPlayInEntityAction.EnumPlayerAction.START_SPRINTING
                || action == PacketPlayInEntityAction.EnumPlayerAction.STOP_SPRINTING) {
            if (state.sprintToggleWindowStartMillis == 0L || now - state.sprintToggleWindowStartMillis >= 450L) {
                state.sprintToggleWindowStartMillis = now;
                state.sprintToggleCount = 1;
            } else {
                state.sprintToggleCount++;
            }

            if (state.sprintToggleCount >= 7) {
                flag(uniqueId, state, "Player.BadPackets/D",
                        0.85D + ((state.sprintToggleCount - 6) * 0.12D),
                        "sprint=" + state.sprintToggleCount + "/450ms",
                        false, true, "sprint-spam", 850L, now);
            }
            return;
        }

        if (action == PacketPlayInEntityAction.EnumPlayerAction.PRESS_SHIFT_KEY
                || action == PacketPlayInEntityAction.EnumPlayerAction.RELEASE_SHIFT_KEY) {
            if (state.sneakToggleWindowStartMillis == 0L || now - state.sneakToggleWindowStartMillis >= 450L) {
                state.sneakToggleWindowStartMillis = now;
                state.sneakToggleCount = 1;
            } else {
                state.sneakToggleCount++;
            }

            if (state.sneakToggleCount >= 7) {
                flag(uniqueId, state, "Player.BadPackets/E",
                        0.85D + ((state.sneakToggleCount - 6) * 0.12D),
                        "sneak=" + state.sneakToggleCount + "/450ms",
                        false, true, "sneak-spam", 850L, now);
            }
        }
    }

    private void handleArmAnimation(UUID uniqueId, PacketState state, long now) {
        if (state.armAnimationWindowStartMillis == 0L || now - state.armAnimationWindowStartMillis >= 500L) {
            state.armAnimationWindowStartMillis = now;
            state.armAnimations = 1;
        } else {
            state.armAnimations++;
        }

        if (state.armAnimations >= 16) {
            flag(uniqueId, state, "Player.BadPackets/F",
                    0.90D + ((state.armAnimations - 15) * 0.10D),
                    "swings=" + state.armAnimations + "/500ms",
                    false, true, "arm-spam", 900L, now);
        }
    }

    private void handleBlockDig(UUID uniqueId, PacketState state, PacketPlayInBlockDig packet, long now) {
        PacketPlayInBlockDig.EnumPlayerDigType digType = packet.d();
        BlockPosition position = packet.b();

        if (digType == PacketPlayInBlockDig.EnumPlayerDigType.START_DESTROY_BLOCK) {
            if (state.digging && state.currentDigPos != null && !state.currentDigPos.equals(position)
                    && now - state.currentDigStartMillis < 40L) {
                flag(uniqueId, state, "Player.BadPackets/G", 1.00D,
                        "dig-switch", false, true, "dig-order", 650L, now);
            }

            state.digging = true;
            state.currentDigPos = position;
            state.currentDigStartMillis = now;
            return;
        }

        if (digType == PacketPlayInBlockDig.EnumPlayerDigType.ABORT_DESTROY_BLOCK) {
            if (state.digging && state.currentDigPos != null && state.currentDigPos.equals(position)) {
                state.digging = false;
            }
            return;
        }

        if (digType == PacketPlayInBlockDig.EnumPlayerDigType.STOP_DESTROY_BLOCK) {
            if (!state.digging || state.currentDigPos == null || !state.currentDigPos.equals(position)) {
                flag(uniqueId, state, "Player.BadPackets/G", 1.20D,
                        "dig-stop-mismatch", false, true, "dig-order", 650L, now);
                state.digging = false;
                state.currentDigPos = null;
                return;
            }

            state.lastFinishedDigPos = position;
            state.lastFinishedDigMillis = now;
            state.lastFinishedDigDurationMillis = now - state.currentDigStartMillis;
            state.digging = false;
            state.currentDigPos = null;
        }
    }

    private void flag(UUID uniqueId, PacketState state, String check, double amount, String details,
                      boolean blocked, boolean punish, String cooldownKey, long cooldownMillis, long now) {
        Long lastFlag = state.flagCooldowns.get(cooldownKey);
        if (lastFlag != null && now - lastFlag.longValue() < cooldownMillis) {
            return;
        }

        state.flagCooldowns.put(cooldownKey, Long.valueOf(now));
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline() || CheckUtil.isCheckBypass(player)) {
                return;
            }

            PlayerData data = this.plugin.getDataManager().get(player);
            double vl = data.addViolation(check, amount);
            this.plugin.getAlertManager().sendViolation(player, check, vl, details, blocked);
            if (punish) {
                this.plugin.getPunishmentManager().considerPunishment(player, check, vl);
            }
        });
    }

    private boolean sameBlock(BlockPosition position, Block block) {
        return position.getX() == block.getX()
                && position.getY() == block.getY()
                && position.getZ() == block.getZ();
    }

    private String handlerName(Player player) {
        return "sandac_packet_" + player.getEntityId();
    }

    private static final class PacketState {
        private final Map<String, Long> flagCooldowns = new ConcurrentHashMap<String, Long>();
        private long flyingWindowStartMillis;
        private int flyingPackets;
        private int timerBuffer;
        private boolean hasPacketPosition;
        private double lastX;
        private double lastY;
        private double lastZ;
        private float lastYaw;
        private float lastPitch;
        private int gridMoveTicks;
        private long sprintToggleWindowStartMillis;
        private int sprintToggleCount;
        private long sneakToggleWindowStartMillis;
        private int sneakToggleCount;
        private long armAnimationWindowStartMillis;
        private int armAnimations;
        private boolean digging;
        private BlockPosition currentDigPos;
        private long currentDigStartMillis;
        private BlockPosition lastFinishedDigPos;
        private long lastFinishedDigMillis;
        private long lastFinishedDigDurationMillis;
        private long lastUseItemMillis;
    }
}
