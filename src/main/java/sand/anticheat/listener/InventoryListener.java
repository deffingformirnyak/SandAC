package sand.anticheat.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import sand.anticheat.SandAC;
import sand.anticheat.data.PlayerData;
import sand.anticheat.util.CheckUtil;

public final class InventoryListener implements Listener {

    private final SandAC plugin;
    private final Set<UUID> openContainers = ConcurrentHashMap.newKeySet();

    public InventoryListener(SandAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (CheckUtil.isCheckBypass(player)) {
            return;
        }

        this.plugin.getDataManager().get(player).markInventoryInteraction();
        if (!involvesPearl(event)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        boolean nonHotbarPearl = clicked == null
                || !(clicked instanceof PlayerInventory)
                || isPlayerMainInventorySlot(event.getSlot())
                || clicked.getType() != InventoryType.PLAYER;

        if (!nonHotbarPearl) {
            return;
        }

        PlayerData data = this.plugin.getDataManager().get(player);
        data.markPearlInventoryClick(CheckUtil.hasHotbarPearl(player), event.getSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        InventoryType type = event.getView().getTopInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE || type == InventoryType.PLAYER) {
            return;
        }

        this.openContainers.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        this.openContainers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl) || !(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity().getShooter();
        if (CheckUtil.isCheckBypass(player)) {
            return;
        }

        PlayerData data = this.plugin.getDataManager().get(player);
        long delta = System.currentTimeMillis() - data.getLastPearlInventoryMillis();
        if (delta > 450L) {
            data.decayViolation("Combat.ClickPearl", 0.20D);
            return;
        }

        boolean suspicious = delta < 250L || !data.isHotbarPearlBeforeClick();
        if (!suspicious) {
            data.decayViolation("Combat.ClickPearl", 0.16D);
            return;
        }

        double vl = data.addViolation("Combat.ClickPearl", delta < 120L ? 1.65D : 1.15D);
        if (vl < 3.00D) {
            this.plugin.getAlertManager().sendViolation(player, "Combat.ClickPearl", vl,
                    "delta=" + delta + "ms, slot=" + data.getLastPearlSlot(), false);
            return;
        }

        event.setCancelled(true);
        this.plugin.getAlertManager().sendViolation(player, "Combat.ClickPearl", vl,
                "delta=" + delta + "ms, slot=" + data.getLastPearlSlot(), true);
    }

    private boolean involvesPearl(InventoryClickEvent event) {
        return isPearl(event.getCurrentItem()) || isPearl(event.getCursor());
    }

    private boolean isPearl(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.ENDER_PEARL;
    }

    private boolean isPlayerMainInventorySlot(int slot) {
        return slot >= 9 && slot <= 35;
    }

    public boolean hasOpenContainer(Player player) {
        return this.openContainers.contains(player.getUniqueId());
    }

    public void clearState(Player player) {
        this.openContainers.remove(player.getUniqueId());
    }
}
