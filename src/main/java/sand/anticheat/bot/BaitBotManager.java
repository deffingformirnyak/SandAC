package sand.anticheat.bot;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.EnumItemSlot;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_16_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_16_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_16_R3.PlayerInteractManager;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;
import sand.anticheat.util.CheckUtil;

public final class BaitBotManager {

    private final SandAC plugin;
    private final Map<UUID, BotHandle> activeBots = new HashMap<UUID, BotHandle>();
    private final Random random = new Random();

    public BaitBotManager(SandAC plugin) {
        this.plugin = plugin;
        for (Player online : Bukkit.getOnlinePlayers()) {
            inject(online);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                tickBots();
            }
        }.runTaskTimer(this.plugin, 2L, 2L);
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

        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(handlerName) != null) {
                return;
            }

            channel.pipeline().addBefore("packet_handler", handlerName, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof PacketPlayInUseEntity) {
                        handleUseEntity(player, (PacketPlayInUseEntity) msg);
                    }
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

    public void arm(Player suspect) {
        if (suspect == null || !suspect.isOnline() || CheckUtil.isCheckBypass(suspect)) {
            return;
        }

        BotHandle handle = this.activeBots.get(suspect.getUniqueId());
        if (handle == null) {
            handle = spawnBot(suspect);
            if (handle == null) {
                return;
            }
            this.activeBots.put(suspect.getUniqueId(), handle);
        }

        handle.lastCombatMillis = System.currentTimeMillis();
    }

    public void despawn(Player suspect) {
        if (suspect == null) {
            return;
        }

        BotHandle handle = this.activeBots.remove(suspect.getUniqueId());
        if (handle == null || !suspect.isOnline()) {
            return;
        }

        sendDestroyPacket(suspect, handle.npc);
    }

    public void clearAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            despawn(online);
            uninject(online);
        }
        this.activeBots.clear();
    }

    private BotHandle spawnBot(Player suspect) {
        try {
            CraftServer craftServer = (CraftServer) Bukkit.getServer();
            MinecraftServer server = craftServer.getServer();
            WorldServer world = ((CraftWorld) suspect.getWorld()).getHandle();
            GameProfile profile = new GameProfile(UUID.randomUUID(), nextBotName());
            EntityPlayer npc = new EntityPlayer(server, world, profile, new PlayerInteractManager(world));

            Location initial = getOrbitLocation(suspect, 0.0D);
            npc.setLocation(initial.getX(), initial.getY(), initial.getZ(), initial.getYaw(), initial.getPitch());
            npc.setNoGravity(true);
            npc.setInvisible(true);
            npc.setSlot(EnumItemSlot.HEAD, CraftItemStack.asNMSCopy(new ItemStack(Material.REDSTONE_BLOCK)));

            sendSpawnPacket(suspect, npc);
            return new BotHandle(npc);
        } catch (Throwable throwable) {
            this.plugin.getLogger().warning("Failed to spawn bait bot: " + throwable.getMessage());
            return null;
        }
    }

    private void tickBots() {
        long now = System.currentTimeMillis();
        for (UUID uniqueId : this.activeBots.keySet().toArray(new UUID[0])) {
            Player suspect = Bukkit.getPlayer(uniqueId);
            BotHandle handle = this.activeBots.get(uniqueId);
            if (suspect == null || !suspect.isOnline() || suspect.isDead() || handle == null || CheckUtil.isCheckBypass(suspect)) {
                if (suspect != null) {
                    despawn(suspect);
                } else {
                    this.activeBots.remove(uniqueId);
                }
                continue;
            }

            if (now - handle.lastCombatMillis > 3000L) {
                despawn(suspect);
                continue;
            }

            Location location = getOrbitLocation(suspect, (now % 2400L) / 2400.0D * (Math.PI * 2.0D));
            handle.npc.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            sendTeleportPacket(suspect, handle.npc);
        }
    }

    private void handleUseEntity(Player suspect, PacketPlayInUseEntity packet) {
        BotHandle handle = this.activeBots.get(suspect.getUniqueId());
        if (handle == null) {
            return;
        }

        if (packet.b() != PacketPlayInUseEntity.EnumEntityUseAction.ATTACK) {
            return;
        }

        if (packet.getEntityId() != handle.npc.getId()) {
            return;
        }

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!suspect.isOnline() || CheckUtil.isCheckBypass(suspect)) {
                return;
            }

            PlayerData data = this.plugin.getDataManager().get(suspect);
            double support = data.getViolation("Combat.KillAura/Track")
                    + data.getViolation("Combat.KillAura/Snap")
                    + data.getViolation("Combat.HitBox");
            double increment = support >= 4.0D ? 1.35D : 1.0D;
            double vl = data.addViolation("Combat.BaitBot", increment);
            double punishVl = vl + Math.min(1.80D, support * 0.45D);

            this.plugin.getAlertManager().sendViolation(
                    suspect,
                    "Combat.BaitBot",
                    vl,
                    "packet-hit, support=" + CheckUtil.format(support),
                    punishVl >= 2.60D
            );
            this.plugin.getPunishmentManager().considerPunishment(suspect, "Combat.BaitBot", punishVl);
            despawn(suspect);
        });
    }

    private Location getOrbitLocation(Player suspect, double phase) {
        Location base = suspect.getLocation().clone();
        org.bukkit.util.Vector look = base.getDirection().setY(0.0D);
        if (look.lengthSquared() <= 1.0E-4D) {
            look.setX(0.0D).setZ(1.0D);
        }
        look.normalize();

        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-look.getZ(), 0.0D, look.getX());
        org.bukkit.util.Vector offset = look.multiply(-2.0D)
                .add(side.multiply(Math.sin(phase) * 0.65D))
                .add(new org.bukkit.util.Vector(0.0D, 0.85D + (Math.cos(phase * 2.0D) * 0.20D), 0.0D));

        Location location = base.add(offset);
        location.setYaw(base.getYaw() + 180.0F);
        location.setPitch(0.0F);
        return location;
    }

    private void sendSpawnPacket(Player viewer, EntityPlayer npc) {
        CraftPlayer craftPlayer = (CraftPlayer) viewer;
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc));
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutNamedEntitySpawn(npc));
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutEntityMetadata(npc.getId(), npc.getDataWatcher(), true));
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutEntityEquipment(
                npc.getId(),
                Collections.singletonList(Pair.of(EnumItemSlot.HEAD, CraftItemStack.asNMSCopy(new ItemStack(Material.REDSTONE_BLOCK))))
        ));
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutEntityHeadRotation(npc, (byte) (npc.yaw * 256.0F / 360.0F)));

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (viewer.isOnline()) {
                ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(
                        new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc)
                );
            }
        }, 10L);
    }

    private void sendTeleportPacket(Player viewer, EntityPlayer npc) {
        CraftPlayer craftPlayer = (CraftPlayer) viewer;
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutEntityTeleport(npc));
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutEntityHeadRotation(npc, (byte) (npc.yaw * 256.0F / 360.0F)));
        craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutEntityMetadata(npc.getId(), npc.getDataWatcher(), false));
    }

    private void sendDestroyPacket(Player viewer, EntityPlayer npc) {
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityDestroy(npc.getId()));
    }

    private String handlerName(Player player) {
        return "sandac_bot_" + player.getEntityId();
    }

    private String nextBotName() {
        return "Sand" + (100 + this.random.nextInt(899));
    }

    private static final class BotHandle {
        private final EntityPlayer npc;
        private long lastCombatMillis = System.currentTimeMillis();

        private BotHandle(EntityPlayer npc) {
            this.npc = npc;
        }
    }
}
